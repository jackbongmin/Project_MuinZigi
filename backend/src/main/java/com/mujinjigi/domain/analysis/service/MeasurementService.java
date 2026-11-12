package com.mujinjigi.domain.analysis.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.mujinjigi.domain.analysis.dto.MeasurementLogResponse;
import com.mujinjigi.domain.analysis.repository.MeasurementLogRepository;
import com.mujinjigi.domain.video.repository.VideoRepository;

/**
 * 1단계 · 05 (측정 로그)
 * 영상 하나의 측정 로그를 꺼내 보여 주는 클래스. 저장은 각 단계의 서비스(FrameService, JudgmentService)가 함
 * 예: 영상 69번 → [프레임 추출 1줄, 판단 1줄]
 */
@Service
public class MeasurementService {

    private final VideoRepository videoRepository;
    private final MeasurementLogRepository measurementLogRepository;

    public MeasurementService(VideoRepository videoRepository, MeasurementLogRepository measurementLogRepository) {
        this.videoRepository = videoRepository;
        this.measurementLogRepository = measurementLogRepository;
    }

    @Transactional(readOnly = true)
    public List<MeasurementLogResponse> findAll(Long videoId) {
        if (!videoRepository.existsById(videoId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "영상이 없음: " + videoId);
        }
        return measurementLogRepository.findByVideoIdOrderByIdAsc(videoId).stream()
                .map(MeasurementLogResponse::from)
                .toList();
    }
}