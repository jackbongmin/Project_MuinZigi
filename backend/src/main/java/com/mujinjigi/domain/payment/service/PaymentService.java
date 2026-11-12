package com.mujinjigi.domain.payment.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.mujinjigi.domain.payment.dto.PaymentRecordResponse;
import com.mujinjigi.domain.payment.dto.PaymentUploadResponse;
import com.mujinjigi.domain.payment.entity.PaymentRecord;
import com.mujinjigi.domain.payment.entity.PaymentType;
import com.mujinjigi.domain.payment.entity.PaymentUpload;
import com.mujinjigi.domain.payment.repository.PaymentRecordRepository;
import com.mujinjigi.domain.payment.repository.PaymentUploadRepository;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * 결제 CSV 파일을 받아 PaymentCsvParser로 검사하고, 업로드 한 건과 결제 기록 여러 줄로 저장하는 클래스. 저장한 것을 다시 조회도 함
 * 흐름: 빈 파일·확장자 검사 → 줄마다 검사 → 결제 합계 계산 → 업로드·기록 저장 (틀린 줄이 하나라도 있으면 아무것도 저장 안 함)
 */
@Service
public class PaymentService {

    private final PaymentUploadRepository paymentUploadRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final PaymentCsvParser paymentCsvParser;

    public PaymentService(PaymentUploadRepository paymentUploadRepository,
                          PaymentRecordRepository paymentRecordRepository,
                          PaymentCsvParser paymentCsvParser) {
        this.paymentUploadRepository = paymentUploadRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.paymentCsvParser = paymentCsvParser;
    }

    // 한 줄이라도 틀리면 아무것도 저장하지 않음. 검사를 저장보다 먼저 끝내고, 저장은 한 트랜잭션으로 묶음
    @Transactional
    public PaymentUploadResponse upload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "빈 파일은 올릴 수 없음");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV 파일만 올릴 수 있음");
        }

        List<PaymentCsvRow> rows;
        try {
            rows = paymentCsvParser.parse(new String(file.getBytes(), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            // 몇 번째 줄이 왜 틀렸는지 그대로 돌려줌
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            throw new UncheckedIOException("CSV 파일을 읽지 못함: " + name, e);
        }

        long totalAmount = rows.stream()
                .filter(row -> row.type() == PaymentType.PAY)
                .mapToLong(PaymentCsvRow::amount)
                .sum();
        PaymentUpload upload = paymentUploadRepository.save(new PaymentUpload(name, rows.size(), totalAmount));
        paymentRecordRepository.saveAll(rows.stream()
                .map(row -> new PaymentRecord(upload, row.paidAt(), row.transactionId(), row.productCode(),
                        row.productName(), row.quantity(), row.unitPrice(), row.amount(), row.type()))
                .toList());
        return PaymentUploadResponse.from(upload);
    }

    @Transactional(readOnly = true)
    public PaymentUploadResponse find(Long uploadId) {
        return PaymentUploadResponse.from(findUpload(uploadId));
    }

    @Transactional(readOnly = true)
    public List<PaymentRecordResponse> findRecords(Long uploadId) {
        findUpload(uploadId);
        return paymentRecordRepository.findByUploadIdOrderByPaidAtAscIdAsc(uploadId).stream()
                .map(PaymentRecordResponse::from)
                .toList();
    }

    private PaymentUpload findUpload(Long uploadId) {
        return paymentUploadRepository.findById(uploadId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "결제 업로드가 없음: " + uploadId));
    }
}