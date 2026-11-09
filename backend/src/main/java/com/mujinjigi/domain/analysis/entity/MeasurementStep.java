package com.mujinjigi.domain.analysis.entity;

/**
 * 1단계 · 05 (측정 로그)
 * 측정 로그를 남기는 처리 단계. 2단계에서 대조 단계가 추가됨
 */
public enum MeasurementStep {

    FRAME_EXTRACTION,   // 프레임 추출과 고르기(FrameService.extract)
    JUDGMENT            // jev 프레임 판단(JudgmentService.judge)

}
