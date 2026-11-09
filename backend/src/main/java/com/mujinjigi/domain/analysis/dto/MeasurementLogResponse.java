package com.mujinjigi.domain.analysis.dto;

import java.time.LocalDateTime;
import java.util.Map;

import com.mujinjigi.domain.analysis.entity.MeasurementLog;
import com.mujinjigi.domain.analysis.entity.MeasurementStep;

/**
 * 1단계 · 05 (측정 로그)
 * 측정 로그 응답
 */
public record MeasurementLogResponse(Long id, MeasurementStep step, boolean success, LocalDateTime startedAt,
                                     long elapsedMs, Integer extractedFrameCount, int frameCount,
                                     int jevCallCount, int retryCount, long inputTokens,
                                     Map<String, Object> settings) {

    public static MeasurementLogResponse from(MeasurementLog log) {
        return new MeasurementLogResponse(
                log.getId(),
                log.getStep(),
                log.isSuccess(),
                log.getStartedAt(),
                log.getElapsedMs(),
                log.getExtractedFrameCount(),
                log.getFrameCount(),
                log.getJevCallCount(),
                log.getRetryCount(),
                log.getInputTokens(),
                log.getSettings());
    }
}