package com.mujinjigi.domain.payment.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.mujinjigi.domain.payment.dto.PaymentRecordResponse;
import com.mujinjigi.domain.payment.dto.PaymentUploadResponse;
import com.mujinjigi.domain.payment.service.PaymentService;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * 결제 CSV 업로드와 조회 API
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // POST /api/payments (form-data, 필드 이름 file) → 201 Created + 업로드 요약, Location 헤더
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PaymentUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        PaymentUploadResponse response = paymentService.upload(file);
        return ResponseEntity.created(URI.create("/api/payments/" + response.id())).body(response);
    }

    // GET /api/payments/{uploadId} → 업로드 요약, 없으면 404
    @GetMapping("/{uploadId}")
    public PaymentUploadResponse find(@PathVariable Long uploadId) {
        return paymentService.find(uploadId);
    }

    // GET /api/payments/{uploadId}/records → 결제 기록 목록 (결제 시각 순), 없으면 404
    @GetMapping("/{uploadId}/records")
    public List<PaymentRecordResponse> findRecords(@PathVariable Long uploadId) {
        return paymentService.findRecords(uploadId);
    }
}