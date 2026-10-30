package com.mujinjigi.domain.analysis.jev;

import java.time.LocalDateTime;

/**
 * 1단계 · 04 (Jev 재시도와 원문 로그)
 * Jev 호출 한 번(시도 한 번)의 결과. 재시도하면 시도마다 하나씩 생김
 *
 * @param attempt      몇 번째 시도인지 (1부터)
 * @param calledAt     이 시도를 보낸 시각. 재시도 사이에 얼마나 기다렸는지 볼 수 있음
 * @param statusCode   HTTP 상태 코드. 연결 실패·시간 초과로 응답을 못 받았으면 null
 * @param responseBody 응답 본문 원문. 응답을 못 받았으면 null
 * @param error        응답을 못 받은 이유 (연결 실패, 시간 초과). 응답을 받았으면 null
 * @param elapsedMs    이 시도에 걸린 시간(밀리초)
 */
public record JevCallAttempt(int attempt, LocalDateTime calledAt, Integer statusCode, String responseBody,
                             String error, long elapsedMs) {
}