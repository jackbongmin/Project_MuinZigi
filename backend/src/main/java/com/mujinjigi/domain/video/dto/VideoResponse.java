package com.mujinjigi.domain.video.dto;

import java.time.LocalDateTime;

import com.mujinjigi.domain.video.entity.Video;

/**
 * 1단계 · 01 (영상 업로드 API)
 * 영상 정보를 API로 돌려줄 때 쓰는 응답 형식. 엔티티를 그대로 내보내지 않고 필요한 값만 담음
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
