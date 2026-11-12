package com.mujinjigi.domain.analysis.dto;

import java.util.Map;

import com.mujinjigi.domain.analysis.entity.FrameJudgment;

/**
 * 1단계 · 03 (Jev 프레임 판단)
 * 판단 API가 돌려주는 JSON 한 칸의 모양
 * 예: {"frameId": 12, "timeSec": 11.0, "questionSetVersion": "v2", "model": "jev-1.14.0", "inputTokens": 2803, "answers": {"person": 0.98, ...}}
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