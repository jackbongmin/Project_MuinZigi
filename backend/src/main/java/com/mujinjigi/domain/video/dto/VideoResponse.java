package com.mujinjigi.domain.video.dto;

import java.time.LocalDateTime;

import com.mujinjigi.domain.video.entity.Video;

/**
 * 1단계 · 01 (영상 업로드 API)
 * 영상 API가 돌려주는 JSON의 모양. 엔티티를 그대로 내보내지 않고 필요한 값만 담음
 * 예: {"id": 69, "originalName": "P1.mp4", "sizeBytes": 1234567, "uploadedAt": "2026-11-03T10:12:00"}
 */
public record VideoResponse(Long id, String originalName, long sizeBytes, LocalDateTime uploadedAt) {

    public static VideoResponse from(Video video) {
        return new VideoResponse(
                video.getId(),
                video.getOriginalName(),
                video.getSizeBytes(),
                video.getUploadedAt());
    }


}
