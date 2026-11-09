package com.mujinjigi.domain.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.mujinjigi.domain.payment.repository.PaymentRecordRepository;
import com.mujinjigi.domain.payment.repository.PaymentUploadRepository;
import com.mujinjigi.domain.payment.service.PaymentCsvParser;

/**
 * 2단계 · 07 (결제 CSV 업로드와 저장)
 * 결제 CSV API 테스트. 실제 DB에 저장하고 테스트가 끝나면 되돌림
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentControllerTest {

    // 같은 거래(T0002)에 상품 두 줄, 취소 한 줄. 일부러 시각 순서를 섞어 둠
    private static final String CSV = PaymentCsvParser.HEADER + "\n"
            + "2026-11-09 14:05:02,T0002,P-COLA,콜라,1,2000,2000,PAY\n"
            + "2026-11-09 14:03:21,T0001,P-CHOCO,초코바,2,1500,3000,PAY\n"
            + "2026-11-09 14:05:02,T0002,P-CHIPS,감자칩,1,1800,1800,PAY\n"
            + "2026-11-09 14:10:40,T0003,P-COLA,콜라,1,2000,2000,CANCEL\n";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PaymentUploadRepository paymentUploadRepository;

    @Autowired
    PaymentRecordRepository paymentRecordRepository;

    @Test
    void CSV를_올리면_201과_요약을_돌려주고_기록을_시각순으로_조회한다() throws Exception {
        String location = mockMvc.perform(multipart("/api/payments").file(csvFile("kiosk_1109.csv", CSV)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalName").value("kiosk_1109.csv"))
                .andExpect(jsonPath("$.recordCount").value(4))
                // 결제 3줄 합계. 취소 줄(2,000원)은 더하지 않음
                .andExpect(jsonPath("$.totalAmount").value(3000 + 2000 + 1800))
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordCount").value(4));

        mockMvc.perform(get(location + "/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].transactionId").value("T0001"))
                .andExpect(jsonPath("$[0].paidAt").value("2026-11-09T14:03:21"))
                .andExpect(jsonPath("$[0].amount").value(3000))
                // 같은 시각이면 CSV에 적힌 순서대로
                .andExpect(jsonPath("$[1].productCode").value("P-COLA"))
                .andExpect(jsonPath("$[2].productCode").value("P-CHIPS"))
                .andExpect(jsonPath("$[3].type").value("CANCEL"));
    }

    @Test
    void 틀린_줄이_있으면_400과_줄_번호를_돌려주고_아무것도_저장하지_않는다() throws Exception {
        long uploadsBefore = paymentUploadRepository.count();
        long recordsBefore = paymentRecordRepository.count();
        String broken = PaymentCsvParser.HEADER + "\n"
                + "2026-11-09 14:03:21,T0001,P-CHOCO,초코바,2,1500,3000,PAY\n"
                + "2026-11-09 14:05:02,T0002,P-COLA,콜라,0,2000,0,PAY\n";

        mockMvc.perform(multipart("/api/payments").file(csvFile("broken.csv", broken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("3번째 줄")));

        // 첫 줄은 맞았지만 저장되지 않음
        assertThat(paymentUploadRepository.count()).isEqualTo(uploadsBefore);
        assertThat(paymentRecordRepository.count()).isEqualTo(recordsBefore);
    }

    @Test
    void CSV가_아닌_파일은_400을_돌려준다() throws Exception {
        mockMvc.perform(multipart("/api/payments").file(csvFile("payments.xlsx", CSV)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 빈_파일은_400을_돌려준다() throws Exception {
        mockMvc.perform(multipart("/api/payments").file(csvFile("empty.csv", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 없는_업로드를_조회하면_404를_돌려준다() throws Exception {
        mockMvc.perform(get("/api/payments/{id}", 999_999)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/payments/{id}/records", 999_999)).andExpect(status().isNotFound());
    }

    private MockMultipartFile csvFile(String name, String content) {
        return new MockMultipartFile("file", name, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }
}
