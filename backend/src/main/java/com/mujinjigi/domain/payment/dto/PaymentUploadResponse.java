package com.mujinjigi.domain.payment.dto;

import java.time.LocalDateTime;

import com.mujinjigi.domain.payment.entity.PaymentUpload;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * 결제 CSV 업로드 API가 돌려주는 요약 JSON의 모양
 * 예: {"id": 2, "originalName": "sample_kiosk.csv", "recordCount": 4, "totalAmount": 6800, ...}
 */
public record PaymentUploadResponse(Long id, String originalName, int recordCount, long totalAmount,
                                    LocalDateTime uploadedAt) {

    public static PaymentUploadResponse from(PaymentUpload upload) {
        return new PaymentUploadResponse(
                upload.getId(),
                upload.getOriginalName(),
                upload.getRecordCount(),
                upload.getTotalAmount(),
                upload.getUploadedAt());
    }
}