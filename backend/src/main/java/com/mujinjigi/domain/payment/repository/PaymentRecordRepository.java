package com.mujinjigi.domain.payment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mujinjigi.domain.payment.entity.PaymentRecord;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * payment_records 테이블에서 결제 기록을 저장·조회하는 저장소
 * 예: findByUploadIdOrderByPaidAtAscIdAsc(2) → 업로드 2번의 기록을 결제 시각 순으로 (같은 시각이면 CSV 순서)
 */
public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

    List<PaymentRecord> findByUploadIdOrderByPaidAtAscIdAsc(Long uploadId);
}