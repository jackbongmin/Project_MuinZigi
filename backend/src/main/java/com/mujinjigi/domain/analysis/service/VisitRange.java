package com.mujinjigi.domain.analysis.service;

/**
 * 2단계 · 08 (방문 나누기)
 * 방문 나누기의 결과 한 줄. "손님이 몇 초부터 몇 초까지 있었나"를 담음 (DB에 저장하기 전)
 * 예: (11.0, 19.0, 9) → 11초에 처음 보이고 19초에 마지막으로 보임. 그 사이 사람이 보인 프레임 9장
 *
 * @param startSec   사람이 처음 보인 시각(초)
 * @param endSec     사람이 마지막으로 보인 시각(초)
 * @param frameCount 방문 안에서 사람이 보인 프레임 수
 */
public record VisitRange(double startSec, double endSec, int frameCount) {
}
