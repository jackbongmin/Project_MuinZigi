package com.mujinjigi.domain.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mujinjigi.domain.payment.entity.PaymentUpload;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * payment_uploads 테이블에서 업로드 기록을 저장·조회하는 저장소
 * 예: findById(2) → 업로드 2번의 요약. save, findById 같은 기본 메서드는 JpaRepository가 만들어 줌
 */
public interface PaymentUploadRepository extends JpaRepository<PaymentUpload, Long> {
}