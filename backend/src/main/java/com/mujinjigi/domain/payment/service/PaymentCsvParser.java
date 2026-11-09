package com.mujinjigi.domain.payment.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.mujinjigi.domain.payment.entity.PaymentType;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * 결제 CSV 글자를 읽어 줄마다 PaymentCsvRow로 바꿈. 틀린 줄이 있으면 몇 번째 줄이 왜 틀렸는지 알려 주고 멈춤
 * DB·Spring 없이 동작해서 단위 테스트로 바로 확인함
 */
@Component
public class PaymentCsvParser {

    public static final String HEADER = "paid_at,transaction_id,product_code,product_name,quantity,unit_price,amount,type";

    private static final int COLUMN_COUNT = 8;
    private static final DateTimeFormatter PAID_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    // 엑셀에서 "CSV UTF-8"로 저장하면 파일 맨 앞에 보이지 않는 글자(BOM)가 붙음. 그대로 두면 헤더가 달라 보임
    private static final String BOM = "﻿";

    public List<PaymentCsvRow> parse(String content) {
        if (content.startsWith(BOM)) {
            content = content.substring(1);
        }
        // \R: 줄바꿈 종류(윈도우 \r\n, 리눅스 \n)와 상관없이 줄을 나눔
        String[] lines = content.split("\\R");

        List<PaymentCsvRow> rows = new ArrayList<>();
        boolean headerChecked = false;
        for (int i = 0; i < lines.length; i++) {
            int lineNumber = i + 1;
            String line = lines[i].strip();
            if (line.isEmpty()) {
                continue;
            }
            if (!headerChecked) {
                if (!line.equals(HEADER)) {
                    throw new IllegalArgumentException(lineNumber + "번째 줄: 헤더가 다름. 필요한 헤더: " + HEADER);
                }
                headerChecked = true;
                continue;
            }
            rows.add(parseLine(line, lineNumber));
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("결제 기록이 한 줄도 없음");
        }
        return rows;
    }

    private PaymentCsvRow parseLine(String line, int lineNumber) {
        // -1: 끝에 빈 칸이 있어도 버리지 않고 칸 수에 셈 ("a,b," → 3칸)
        String[] columns = line.split(",", -1);
        if (columns.length != COLUMN_COUNT) {
            throw error(lineNumber, "칸이 " + COLUMN_COUNT + "개여야 함 (" + columns.length + "개)");
        }

        LocalDateTime paidAt = parsePaidAt(columns[0].strip(), lineNumber);
        String transactionId = required(columns[1], "transaction_id", lineNumber);
        String productCode = required(columns[2], "product_code", lineNumber);
        String productName = required(columns[3], "product_name", lineNumber);
        long quantity = parseNumber(columns[4], "quantity", lineNumber);
        long unitPrice = parseNumber(columns[5], "unit_price", lineNumber);
        long amount = parseNumber(columns[6], "amount", lineNumber);
        PaymentType type = parseType(columns[7].strip(), lineNumber);

        if (quantity < 1) {
            throw error(lineNumber, "quantity는 1 이상이어야 함 (" + quantity + ")");
        }
        if (unitPrice < 0) {
            throw error(lineNumber, "unit_price는 0 이상이어야 함 (" + unitPrice + ")");
        }
        // 금액이 수량 × 단가와 다르면 CSV가 깨졌거나 다른 형식임. 대조 결과를 믿을 수 없으므로 받지 않음
        if (amount != quantity * unitPrice) {
            throw error(lineNumber, "amount가 quantity × unit_price와 다름 (" + amount + " ≠ " + quantity + " × " + unitPrice + ")");
        }

        return new PaymentCsvRow(paidAt, transactionId, productCode, productName, quantity, unitPrice, amount, type);
    }

    private LocalDateTime parsePaidAt(String value, int lineNumber) {
        try {
            return LocalDateTime.parse(value, PAID_AT_FORMAT);
        } catch (DateTimeParseException e) {
            throw error(lineNumber, "paid_at 형식은 yyyy-MM-dd HH:mm:ss 여야 함 (" + value + ")");
        }
    }

    private long parseNumber(String value, String name, int lineNumber) {
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException e) {
            throw error(lineNumber, name + "는 정수여야 함 (" + value + ")");
        }
    }

    private PaymentType parseType(String value, int lineNumber) {
        try {
            return PaymentType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw error(lineNumber, "type은 PAY 또는 CANCEL이어야 함 (" + value + ")");
        }
    }

    private String required(String value, String name, int lineNumber) {
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            throw error(lineNumber, name + "가 비어 있음");
        }
        return stripped;
    }

    private IllegalArgumentException error(int lineNumber, String message) {
        return new IllegalArgumentException(lineNumber + "번째 줄: " + message);
    }
}