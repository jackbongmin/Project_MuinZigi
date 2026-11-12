package com.mujinjigi.domain.payment.dto;

import java.time.LocalDateTime;

import com.mujinjigi.domain.payment.entity.PaymentRecord;
import com.mujinjigi.domain.payment.entity.PaymentType;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * 결제 기록 API가 돌려주는 JSON 한 칸의 모양
 * 예: {"paidAt": "2026-11-09T14:03:21", "productCode": "P-CHOCO", "productName": "초코바", "quantity": 2, "unitPrice": 1500, "amount": 3000, "type": "PAY", ...}
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