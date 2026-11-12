package com.mujinjigi.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mujinjigi.domain.payment.entity.PaymentType;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * PaymentCsvParser가 CSV를 제대로 읽고 틀린 줄을 잡아내는지 확인하는 테스트. Spring·DB 없이 글자를 넣고 결과만 확인함
 * 예: 금액이 수량 × 단가와 다른 줄을 넣으면 → "N번째 줄: ..." 예외가 나는지
 */
class PaymentCsvParserTest {

    private static final String HEADER = PaymentCsvParser.HEADER;

    PaymentCsvParser parser = new PaymentCsvParser();

    @Test
    void 줄마다_결제_기록을_만든다() {
        String csv = HEADER + "\n"
                + "2026-11-09 14:03:21,T0001,P-CHOCO,초코바,2,1500,3000,PAY\n"
                + "2026-11-09 14:05:02,T0002,P-COLA,콜라,1,2000,2000,CANCEL\n";

        List<PaymentCsvRow> rows = parser.parse(csv);

        assertThat(rows).hasSize(2);
        PaymentCsvRow first = rows.get(0);
        assertThat(first.paidAt()).isEqualTo(LocalDateTime.of(2026, 11, 9, 14, 3, 21));
        assertThat(first.transactionId()).isEqualTo("T0001");
        assertThat(first.productCode()).isEqualTo("P-CHOCO");
        assertThat(first.productName()).isEqualTo("초코바");
        assertThat(first.quantity()).isEqualTo(2);
        assertThat(first.unitPrice()).isEqualTo(1500);
        assertThat(first.amount()).isEqualTo(3000);
        assertThat(first.type()).isEqualTo(PaymentType.PAY);
        assertThat(rows.get(1).type()).isEqualTo(PaymentType.CANCEL);
    }

    @Test
    void 엑셀이_붙인_BOM과_윈도우_줄바꿈과_빈_줄은_넘어간다() {
        String csv = "﻿" + HEADER + "\r\n"
                + "\r\n"
                + "2026-11-09 14:03:21,T0001,P-CHOCO,초코바,1,1500,1500,PAY\r\n"
                + "\r\n";

        List<PaymentCsvRow> rows = parser.parse(csv);

        assertThat(rows).hasSize(1);
    }

    @Test
    void 헤더가_다르면_예외를_던진다() {
        String csv = "시각,거래번호,상품코드,상품명,수량,단가,금액,구분\n"
                + "2026-11-09 14:03:21,T0001,P-CHOCO,초코바,1,1500,1500,PAY\n";

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parser.parse(csv));

        assertThat(e.getMessage()).contains("1번째 줄").contains("헤더");
    }

    @Test
    void 결제_기록이_없으면_예외를_던진다() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parser.parse(HEADER + "\n"));

        assertThat(e.getMessage()).contains("한 줄도 없음");
    }

    @Test
    void 칸_수가_다르면_줄_번호와_함께_예외를_던진다() {
        // 상품 이름에 쉼표가 들어가면 칸이 하나 늘어남 (따옴표로 감싼 칸은 지원하지 않음)
        String csv = HEADER + "\n"
                + "2026-11-09 14:03:21,T0001,P-CHOCO,초코바,1,1500,1500,PAY\n"
                + "2026-11-09 14:05:02,T0002,P-SNACK,과자,대용량,1,3000,3000,PAY\n";

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parser.parse(csv));

        assertThat(e.getMessage()).contains("3번째 줄").contains("9개");
    }

    @Test
    void 금액이_수량_곱하기_단가와_다르면_예외를_던진다() {
        String csv = HEADER + "\n"
                + "2026-11-09 14:03:21,T0001,P-CHOCO,초코바,10,1500,12000,PAY\n";

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parser.parse(csv));

        assertThat(e.getMessage()).contains("2번째 줄").contains("12000 ≠ 10 × 1500");
    }

    @Test
    void 칸_값이_틀리면_어느_칸이_틀렸는지_알려준다() {
        assertThat(errorOf("2026/11/09 14:03:21,T0001,P-CHOCO,초코바,1,1500,1500,PAY")).contains("paid_at");
        assertThat(errorOf("2026-11-09 14:03:21, ,P-CHOCO,초코바,1,1500,1500,PAY")).contains("transaction_id가 비어 있음");
        assertThat(errorOf("2026-11-09 14:03:21,T0001,P-CHOCO,초코바,한개,1500,1500,PAY")).contains("quantity는 정수");
        assertThat(errorOf("2026-11-09 14:03:21,T0001,P-CHOCO,초코바,0,1500,0,PAY")).contains("quantity는 1 이상");
        assertThat(errorOf("2026-11-09 14:03:21,T0001,P-CHOCO,초코바,1,-1500,-1500,PAY")).contains("unit_price는 0 이상");
        assertThat(errorOf("2026-11-09 14:03:21,T0001,P-CHOCO,초코바,1,1500,1500,REFUND")).contains("PAY 또는 CANCEL");
    }

    // 헤더 + 한 줄짜리 CSV를 읽혀서 예외 메시지를 돌려줌
    private String errorOf(String line) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(HEADER + "\n" + line + "\n"));
        return e.getMessage();
    }
}
