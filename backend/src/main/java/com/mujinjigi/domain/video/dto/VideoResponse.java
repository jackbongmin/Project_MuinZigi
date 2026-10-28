package com.mujinjigi.domain.video.dto;

import java.time.LocalDateTime;

import com.mujinjigi.domain.video.entity.Video;

public record VideoResponse(Long id, String originalName, long sizeBytes, LocalDateTime uploadedAt) {

    public static VideoResponse from(Video video) {
        return new VideoResponse(
                video.getId(),
                video.getOriginalName(),
                video.getSizeBytes(),
                video.getUploadedAt());
    }


}
