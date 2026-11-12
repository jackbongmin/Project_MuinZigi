package com.mujinjigi.domain.analysis.jev;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 0단계 · Jev 이미지 요청 보내 보기
 * Jev가 돌려준 JSON 응답을 담는 그릇. 지금 쓰는 칸(모델 이름, 질문별 답, 토큰 수)만 받고 나머지는 무시함
 * 예: {"model": "jev-1.14.0", "answers": {"person": {"type": "noul", "noul": 0.98}}, "usage": {"input_tokens": 2803, ...}}
 *
 * @param model   실제로 답한 모델 이름 (로그에 남길 값)
 * @param answers 질문 이름 → 답
 * @param usage   토큰 사용량 (입력 토큰만 과금됨)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JevResponse(String model, Map<String, Answer> answers, Usage usage) {

    /**
     * @param noul 예/아니오 질문에서 "예"일 확률 (0~1)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Answer(String type, Double noul) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(@JsonProperty("input_tokens") int inputTokens,
                        @JsonProperty("output_tokens") int outputTokens) {
    }
}
