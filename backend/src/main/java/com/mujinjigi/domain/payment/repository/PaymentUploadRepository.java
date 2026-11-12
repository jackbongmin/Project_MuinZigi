package com.mujinjigi.domain.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mujinjigi.domain.payment.entity.PaymentUpload;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * PaymentUpload 저장·조회
 */
public interface PaymentUploadRepository extends JpaRepository<PaymentUpload, Long> {
}