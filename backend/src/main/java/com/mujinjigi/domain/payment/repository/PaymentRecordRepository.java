package com.mujinjigi.domain.payment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mujinjigi.domain.payment.entity.PaymentRecord;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * PaymentRecord 저장·조회. 업로드 하나의 기록을 결제 시각 순으로 꺼냄 (같은 시각이면 저장 순서)
 */
public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

    List<PaymentRecord> findByUploadIdOrderByPaidAtAscIdAsc(Long uploadId);
}