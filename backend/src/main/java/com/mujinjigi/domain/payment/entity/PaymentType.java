package com.mujinjigi.domain.payment.entity;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * 결제 기록이 결제인지 취소인지 나타내는 값. CSV의 type 칸에 이 이름 그대로 적힘
 * 예: PAY → 결제, CANCEL → 취소. 취소를 결제 수량에서 빼는 처리는 3단계에서 함
 */

public enum PaymentType {

    PAY,    // 결제
    CANCEL  // 취소, 환불

}
