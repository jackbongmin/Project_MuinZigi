package com.mujinjigi.domain.payment.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * 키오스크 결제 CSV의 한 줄을 DB(payment_records 테이블)에 저장하는 엔티티
 * 예: 2026-11-09 14:03:21, T0001, P-CHOCO 초코바 2개 × 1,500원 = 3,000원, PAY
 * 금액·수량은 원 단위 long으로 저장함 (double은 0.1 같은 값을 정확히 못 담아 합계에 오차가 생김)
 */

@Entity
@Table(name = "payment_records")
public class PaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 여러 결제 기록이 업로드 하나에 속함
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upload_id")
    private PaymentUpload upload;

    // 키오스크 시계 기준 결제 시각. CCTV 시계와 다를 수 있음 (시각 맞추기는 판정 로직에서 함)
    @Column(nullable = false)
    private LocalDateTime paidAt;

    // 키오스크 거래 번호. 한 번 결제에 상품이 여러 개면 같은 번호가 여러 줄에 나옴
    @Column(nullable = false)
    private String transactionId;

    // 상품 코드. 진열대 칸과 결제를 이을 때 이 값을 씀 (상품 이름은 바뀔 수 있음)
    @Column(nullable = false)
    private String productCode;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false)
    private long unitPrice;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType type;

    protected PaymentRecord() {
    }

    public PaymentRecord(PaymentUpload upload, LocalDateTime paidAt, String transactionId, String productCode,
                         String productName, long quantity, long unitPrice, long amount, PaymentType type) {
        this.upload = upload;
        this.paidAt = paidAt;
        this.transactionId = transactionId;
        this.productCode = productCode;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.amount = amount;
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getProductName() {
        return productName;
    }

    public long getQuantity() {
        return quantity;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public long getAmount() {
        return amount;
    }

    public PaymentType getType() {
        return type;
    }
}
