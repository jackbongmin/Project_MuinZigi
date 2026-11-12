package com.mujinjigi.domain.video.service;

/**
 * 1단계 · 02 (프레임 추출과 고르기)
 * FFmpeg가 뽑은 프레임 한 장의 정보. DB에 저장하기 전 단계라 아직 엔티티(VideoFrame)가 아님
 * 예: ("frame_0012.jpg", 11.0, 0.031) → 11초 프레임, 직전 프레임과 다른 정도 0.031
 *
 * @param fileName   프레임 파일 이름 (frame_0001.jpg)
 * @param timeSec    영상 안에서의 시각(초)
 * @param sceneScore 직전 프레임과 얼마나 다른지 (0~1, 첫 프레임은 0)
 */
public record ExtractedFrame(String fileName, double timeSec, double sceneScore) {
}
