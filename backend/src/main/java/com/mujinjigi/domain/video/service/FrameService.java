package com.mujinjigi.domain.video.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.mujinjigi.domain.video.dto.VideoFrameResponse;
import com.mujinjigi.domain.video.entity.Video;
import com.mujinjigi.domain.video.entity.VideoFrame;
import com.mujinjigi.domain.video.repository.VideoFrameRepository;
import com.mujinjigi.domain.video.repository.VideoRepository;

/**
 * 1단계 · 02 (프레임 추출과 고르기)
 * 영상에서 프레임을 뽑고, Jev에 보낼 프레임만 골라 저장함
 */
@Service
public class FrameService {

    private final VideoRepository videoRepository;
    private final VideoFrameRepository videoFrameRepository;
    private final VideoFileStorage videoFileStorage;
    private final FrameExtractor frameExtractor;
    private final FrameSelector frameSelector;
    private final Path frameDir;

    public FrameService(VideoRepository videoRepository,
                        VideoFrameRepository videoFrameRepository,
                        VideoFileStorage videoFileStorage,
                        FrameExtractor frameExtractor,
                        FrameSelector frameSelector,
                        @Value("${app.storage.frame-dir}") String frameDir) {
        this.videoRepository = videoRepository;
        this.videoFrameRepository = videoFrameRepository;
        this.videoFileStorage = videoFileStorage;
        this.frameExtractor = frameExtractor;
        this.frameSelector = frameSelector;
        this.frameDir = Path.of(frameDir).toAbsolutePath().normalize();
    }

    @Transactional
    public List<VideoFrameResponse> extract(Long videoId) {
        Video video = findVideo(videoId);
        if (videoFrameRepository.existsByVideoId(videoId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 프레임을 추출한 영상임: " + videoId);
        }

        // 1. 1초에 1장씩 모두 뽑음 → 2. 보낼 프레임만 고름 → 3. 나머지 파일은 지움 → 4. 고른 프레임을 DB에 저장
        Path outputDir = frameDir.resolve(String.valueOf(videoId));
        List<ExtractedFrame> all = frameExtractor.extract(videoFileStorage.pathOf(video.getStoredName()), outputDir);
        List<ExtractedFrame> selected = frameSelector.select(all);
        deleteUnselected(all, selected, outputDir);

        return selected.stream()
                .map(frame -> videoFrameRepository.save(
                        new VideoFrame(video, frame.timeSec(), frame.sceneScore(), frame.fileName())))
                .map(VideoFrameResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VideoFrameResponse> findAll(Long videoId) {
        findVideo(videoId);
        return videoFrameRepository.findByVideoIdOrderByTimeSecAsc(videoId).stream()
                .map(VideoFrameResponse::from)
                .toList();
    }

    // 프레임 이미지 파일의 실제 경로(Jev에 보낼 때 씀) 프레임 폴더 위치는 이 클래스만 알고있음.
    public Path imagePath(Long videoId, String fileName) {
        return frameDir.resolve(String.valueOf(videoId)).resolve(fileName);
    }

    private Video findVideo(Long videoId) {
        return videoRepository.findById(videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상이 없음: " + videoId));
    }

    private void deleteUnselected(List<ExtractedFrame> all, List<ExtractedFrame> selected, Path outputDir) {
        Set<ExtractedFrame> keep = new HashSet<>(selected);
        try {
            for (ExtractedFrame frame : all) {
                if (!keep.contains(frame)) {
                    Files.deleteIfExists(outputDir.resolve(frame.fileName()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("고르지 않은 프레임 삭제 실패: " + outputDir, e);
        }
    }
}