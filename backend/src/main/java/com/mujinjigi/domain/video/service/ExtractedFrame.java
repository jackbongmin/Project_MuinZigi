package com.mujinjigi.domain.video.service;

/**
 * FFmpeg가 뽑은 프레임 한 장의 정보
 *
 * @param fileName   프레임 파일 이름 (frame_0001.jpg)
 * @param timeSec    영상 안에서의 시각(초)
 * @param sceneScore 직전 프레임과 얼마나 다른지 (0~1, 첫 프레임은 0)
 */
public record ExtractedFrame(String fileName, double timeSec, double sceneScore) {
}
