package com.mujinjigi.domain.payment.dto;

import java.time.LocalDateTime;

import com.mujinjigi.domain.payment.entity.PaymentRecord;
import com.mujinjigi.domain.payment.entity.PaymentType;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * 결제 기록 한 줄 응답
 */
public record PaymentRecordResponse(Long id, LocalDateTime paidAt, String transactionId, String productCode,
                                    String productName, long quantity, long unitPrice, long amount,
                                    PaymentType type) {

    public static PaymentRecordResponse from(PaymentRecord record) {
        return new PaymentRecordResponse(
                record.getId(),
                record.getPaidAt(),
                record.getTransactionId(),
                record.getProductCode(),
                record.getProductName(),
                record.getQuantity(),
                record.getUnitPrice(),
                record.getAmount(),
                record.getType());
    }
}