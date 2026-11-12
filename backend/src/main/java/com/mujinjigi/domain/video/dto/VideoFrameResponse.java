package com.mujinjigi.domain.video.dto;

import com.mujinjigi.domain.video.entity.VideoFrame;

/**
 * 1단계 · 02 (프레임 추출과 고르기)
 * 프레임 API가 돌려주는 JSON 한 칸의 모양. 파일 이름은 내부 정보라 빼고 시각과 점수만 돌려줌
 * 예: {"id": 12, "timeSec": 11.0, "sceneScore": 0.031}
 */
public record VideoFrameResponse(Long id, double timeSec, double sceneScore) {

    public static VideoFrameResponse from(VideoFrame frame) {
        return new VideoFrameResponse(frame.getId(), frame.getTimeSec(), frame.getSceneScore());
    }
}