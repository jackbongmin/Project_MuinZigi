package com.mujinjigi.domain.analysis.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.mujinjigi.domain.analysis.dto.FrameJudgmentResponse;
import com.mujinjigi.domain.analysis.entity.FrameJudgment;
import com.mujinjigi.domain.analysis.entity.JevCallLog;
import com.mujinjigi.domain.analysis.jev.FrameQuestions;
import com.mujinjigi.domain.analysis.jev.JevCallAttempt;
import com.mujinjigi.domain.analysis.jev.JevCallException;
import com.mujinjigi.domain.analysis.jev.JevClient;
import com.mujinjigi.domain.analysis.jev.JevResponse;
import com.mujinjigi.domain.analysis.jev.JevResult;
import com.mujinjigi.domain.analysis.repository.FrameJudgmentRepository;
import com.mujinjigi.domain.analysis.repository.JevCallLogRepository;
import com.mujinjigi.domain.video.entity.VideoFrame;
import com.mujinjigi.domain.video.repository.VideoFrameRepository;
import com.mujinjigi.domain.video.repository.VideoRepository;
import com.mujinjigi.domain.video.service.FrameService;


/**
 * 1단계 · 03 (Jev 프레임 판단)
 * 1단계 · 04 (Jev 호출 기록 저장, 실패 시 502)
 * 저장된 프레임을 Jev에 보내 판단 결과를 저장함
 */
@Service
public class JudgmentService {

    private final VideoRepository videoRepository;
    private final VideoFrameRepository videoFrameRepository;
    private final FrameJudgmentRepository frameJudgmentRepository;
    private final JevCallLogRepository jevCallLogRepository;
    private final FrameService frameService;
    private final JevClient jevClient;

    public JudgmentService(VideoRepository videoRepository,
                           VideoFrameRepository videoFrameRepository,
                           FrameJudgmentRepository frameJudgmentRepository,
                           JevCallLogRepository jevCallLogRepository,
                           FrameService frameService,
                           JevClient jevClient) {
        this.videoRepository = videoRepository;
        this.videoFrameRepository = videoFrameRepository;
        this.frameJudgmentRepository = frameJudgmentRepository;
        this.jevCallLogRepository = jevCallLogRepository;
        this.frameService = frameService;
        this.jevClient = jevClient;
    }

    // @Transactional을 붙히지 안ㅇ흠.
    // Jev 호출(프레임당 약 1초)을 하는 동안 DB 연결을 붙잡지 않으려고 프레임 하나를 판단할 때마다 save()로 바로 저장함
    // (save()는 그 자체로 짧은 트랜잭션)
    public List<FrameJudgmentResponse> judge(Long videoId) {
        checkVideoExists(videoId);
        List<VideoFrame> frames = videoFrameRepository.findByVideoIdOrderByTimeSecAsc(videoId);
        if(frames.isEmpty()){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "프레임을 먼저 추출해야 함: " + videoId);
        }

        Set<Long> judgedFrameIds = frameJudgmentRepository.findAllWithFrame(videoId, FrameQuestions.VERSION).stream()
                .map(judgment -> judgment.getFrame().getId())
                .collect(Collectors.toSet());

        for (VideoFrame frame : frames) {
            if (!judgedFrameIds.contains(frame.getId())) {
                frameJudgmentRepository.save(judgeOne(videoId, frame));
            }
        }
        return findAll(videoId);
    }

    public List<FrameJudgmentResponse> findAll(Long videoId) {
        checkVideoExists(videoId);
        return frameJudgmentRepository.findAllWithFrame(videoId, FrameQuestions.VERSION).stream()
                .map(FrameJudgmentResponse::from)
                .toList();
    }


    private FrameJudgment judgeOne(Long videoId, VideoFrame frame) {
        byte[] image = readImage(videoId, frame);
        JevResult result;
        try {
            result = jevClient.askYesNo(image, "jpg", FrameQuestions.QUESTIONS);
        } catch (JevCallException e) {
            // 재시도까지 실패함. 실패한 시도도 기록으로 남기고, 외부 서버 문제라는 뜻으로 502를 돌려줌
            // 이미 판단한 프레임은 저장돼 있어서, 다시 부르면 남은 프레임부터 이어서 판단함
            saveCallLogs(frame, e.getRequestBody(), e.getAttempts());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    e.getMessage() + " (프레임 " + frame.getTimeSec() + "초)", e);
        }
        saveCallLogs(frame, result.requestBody(), result.attempts());
        JevResponse response = result.response();

        // 응답 형식(Answer 객체)을 그대로 저장하지 않고, 질문 이름 → 확률만 꺼내서 저장함
        Map<String, Double> answers = new LinkedHashMap<>();
        response.answers().forEach((name, answer) -> answers.put(name, answer.noul()));

        return new FrameJudgment(frame, FrameQuestions.VERSION, response.model(),
                response.usage().inputTokens(), answers);
    }


    // 시도마다 한 줄씩 저장함 (재시도했으면 여러 줄)
    private void saveCallLogs(VideoFrame frame, String requestBody, List<JevCallAttempt> attempts) {
        jevCallLogRepository.saveAll(attempts.stream()
                .map(attempt -> new JevCallLog(frame, requestBody, attempt))
                .toList());
    }

    private byte[] readImage(Long videoId, VideoFrame frame) {
        try {
            return Files.readAllBytes(frameService.imagePath(videoId, frame.getFileName()));
        } catch (IOException e) {
            throw new UncheckedIOException("프레임 이미지를 읽지 못함: " + frame.getFileName(), e);
        }
    }

    private void checkVideoExists(Long videoId) {
        if (!videoRepository.existsById(videoId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "영상이 없음: " + videoId);
        }
    }
}