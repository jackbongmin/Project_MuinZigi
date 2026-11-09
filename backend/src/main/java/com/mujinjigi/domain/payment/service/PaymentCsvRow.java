package com.mujinjigi.domain.payment.service;

import java.time.LocalDateTime;

import com.mujinjigi.domain.payment.entity.PaymentType;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * CSV 한줄을 읽어 검사까지 마친 값. 엔티티(PaymentRecord)를 만들기 전 단계라 DB 업로드와 상관없음
 */

public record PaymentCsvRow(
        LocalDateTime paidAt,
        String transactionId,
        String productCode,
        String productName,
        long quantity,
        long unitPrice,
        long amount,
        PaymentType type) {
}
