package com.mujinjigi.domain.analysis.dto;

import java.util.Map;

import com.mujinjigi.domain.analysis.entity.FrameJudgment;

/**
 * 1단계 · 03 (Jev 프레임 판단)
 * 프레임 판단 결과 응답
 */
public record FrameJudgmentResponse(Long frameId, double timeSec, String questionSetVersion, String model,
                                    int inputTokens, Map<String, Double> answers) {

    public static FrameJudgmentResponse from(FrameJudgment judgment) {
        return new FrameJudgmentResponse(
                judgment.getFrame().getId(),
                judgment.getFrame().getTimeSec(),
                judgment.getQuestionSetVersion(),
                judgment.getModel(),
                judgment.getInputTokens(),
                judgment.getAnswers());
    }
}