package com.mujinjigi.domain.analysis.service;

/**
 * 2단계 · 08 (방문 나누기)
 * 방문 나누기에 넣는 입력 한 줄. "몇 초 프레임에 사람이 있을 확률이 얼마인가"를 담음
 * 예: (11.0, 0.98) → 11초 프레임에 사람이 있을 확률 98%
 *
 * @param timeSec           영상 안에서의 시각(초)
 * @param personProbability Jev가 답한 person 질문의 "예" 확률 (0~1)
 */
public record PersonFrame(double timeSec, double personProbability) {
}
