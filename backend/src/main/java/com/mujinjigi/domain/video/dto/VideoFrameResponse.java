package com.mujinjigi.domain.video.dto;

import com.mujinjigi.domain.video.entity.VideoFrame;

/**
 * 1단계 · 02 (프레임 추출과 고르기)
 * 프레임 정보 응답. 파일 이름은 내부 정보라 빼고 시각과 점수만 돌려줌
 */
public record VideoFrameResponse(Long id, double timeSec, double sceneScore) {

    public static VideoFrameResponse from(VideoFrame frame) {
        return new VideoFrameResponse(frame.getId(), frame.getTimeSec(), frame.getSceneScore());
    }
}