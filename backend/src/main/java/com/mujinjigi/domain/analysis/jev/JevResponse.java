package com.mujinjigi.domain.analysis.jev;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Jev 응답. 지금 쓰는 필드만 받고 나머지는 무시함
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
