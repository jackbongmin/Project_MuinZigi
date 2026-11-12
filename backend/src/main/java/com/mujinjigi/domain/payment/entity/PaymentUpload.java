package com.mujinjigi.domain.payment.entity;


import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * 결제 CSV 파일 하나를 올린 기록을 DB(payment_uploads 테이블)에 저장하는 엔티티. 결제 기록 여러 줄을 묶고 요약을 같이 저장함
 * 예: sample_kiosk.csv → 4줄, 결제 합계 6,800원
 */

@Entity
@Table(name = "payment_uploads")
public class PaymentUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 사용자가 올린 파일 이름 (화면에 보여줄 용도). CSV는 내용을 DB에 저장하므로 파일은 디스크에 남기지 않음.
    @Column(nullable = false)
    private String originalName;

    @Column(nullable = false)
    private int recordCount;

    // 결제(PAY) 행 금액의 합계(원). 취소 행은 더하지 않음
    @Column(nullable = false)
    private long totalAmount;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    protected PaymentUpload() {

    }

    public PaymentUpload(String originalName, int recordCount, long totalAmount) {
        this.originalName = originalName;
        this.recordCount = recordCount;
        this.totalAmount = totalAmount;
        this.uploadedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getOriginalName() {
        return originalName;
    }

    public int getRecordCount() {
        return recordCount;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

}
