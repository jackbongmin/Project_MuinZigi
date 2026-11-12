package com.mujinjigi.domain.analysis.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.mujinjigi.domain.analysis.dto.VisitResponse;
import com.mujinjigi.domain.analysis.entity.FrameJudgment;
import com.mujinjigi.domain.analysis.entity.Visit;
import com.mujinjigi.domain.analysis.jev.FrameQuestions;
import com.mujinjigi.domain.analysis.repository.FrameJudgmentRepository;
import com.mujinjigi.domain.analysis.repository.VisitRepository;
import com.mujinjigi.domain.video.entity.Video;
import com.mujinjigi.domain.video.repository.VideoRepository;

/**
 * 2단계 · 08 (방문 나누기)
 * 영상의 판단 결과를 DB에서 꺼내 VisitSplitter로 방문을 자르고, 결과를 visits 테이블에 저장하는 클래스
 * 흐름: 판단 결과 읽기 → (시각, 사람 확률)만 뽑기 → 방문 자르기 → 예전 방문 지우고 새로 저장
 */
@Service
public class VisitService {

    private final VideoRepository videoRepository;
    private final FrameJudgmentRepository frameJudgmentRepository;
    private final VisitRepository visitRepository;
    private final VisitSplitter visitSplitter;

    public VisitService(VideoRepository videoRepository,
                        FrameJudgmentRepository frameJudgmentRepository,
                        VisitRepository visitRepository,
                        VisitSplitter visitSplitter) {
        this.videoRepository = videoRepository;
        this.frameJudgmentRepository = frameJudgmentRepository;
        this.visitRepository = visitRepository;
        this.visitSplitter = visitSplitter;
    }

    // 이전 방문 지우기와 새 방문 저장을 한 트랜잭션으로 묶음. 저장 중 오류가 나면 이전 방문이 그대로 남음
    @Transactional
    public List<VisitResponse> split(Long videoId) {
        Video video = findVideo(videoId);
        List<FrameJudgment> judgments = frameJudgmentRepository.findAllWithFrame(videoId, FrameQuestions.VERSION);
        if (judgments.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "프레임 판단을 먼저 해야 함: " + videoId);
        }

        // 판단 결과에서 시각과 person 확률만 꺼냄. 판단 결과는 이미 시각 순서로 읽힘
        List<PersonFrame> frames = judgments.stream()
                .map(judgment -> new PersonFrame(
                        judgment.getFrame().getTimeSec(),
                        judgment.getAnswers().getOrDefault("person", 0.0)))
                .toList();

        // 설정을 바꿔 다시 나눌 수 있게, 이전 결과를 지우고 새로 저장함
        visitRepository.deleteByVideoId(videoId);
        List<Visit> visits = visitSplitter.split(frames).stream()
                .map(range -> new Visit(video, range.startSec(), range.endSec(), range.frameCount()))
                .toList();
        visitRepository.saveAll(visits);

        return visits.stream().map(VisitResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<VisitResponse> findAll(Long videoId) {
        findVideo(videoId);
        return visitRepository.findByVideoIdOrderByStartSecAsc(videoId).stream()
                .map(VisitResponse::from)
                .toList();
    }

    private Video findVideo(Long videoId) {
        return videoRepository.findById(videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상이 없음: " + videoId));
    }
}