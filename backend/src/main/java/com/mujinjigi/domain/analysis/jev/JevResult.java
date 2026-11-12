package com.mujinjigi.domain.analysis.jev;

import java.util.List;

/**
 * 1단계 · 04 (Jev 재시도와 원문 로그)
 * Jev 호출이 성공했을 때 돌려주는 묶음: 읽은 응답 + 로그로 남길 요청 원문 + 시도 기록
 * 예: 1번째 시도 529, 2번째 시도 200 → attempts에 2개, 마지막 것이 성공한 시도
 *
 * @param response    읽은 응답
 * @param requestBody 보낸 요청 본문 원문 (이미지 base64는 크기만 남기고 뺌)
 * @param attempts    시도 기록. 마지막 시도가 성공한 시도임
 */
public record JevResult(JevResponse response, String requestBody, List<JevCallAttempt> attempts) {
}
