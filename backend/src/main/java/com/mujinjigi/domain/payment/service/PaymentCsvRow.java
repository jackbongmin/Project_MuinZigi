package com.mujinjigi.domain.payment.service;

import java.time.LocalDateTime;

import com.mujinjigi.domain.payment.entity.PaymentType;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * CSV 한 줄을 읽고 검사까지 마친 값. DB에 저장하기 전 단계라 아직 엔티티(PaymentRecord)가 아님
 * 예: (2026-11-09T14:03:21, "T0001", "P-CHOCO", "초코바", 2, 1500, 3000, PAY)
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
