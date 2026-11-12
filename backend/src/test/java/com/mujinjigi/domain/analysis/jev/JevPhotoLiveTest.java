package com.mujinjigi.domain.analysis.jev;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 0단계 · 미결제 상황 판단 1차 테스트 (사진)
 * 실험용 테스트: 매장 상황 사진에 질문 세트를 보내고 질문별 확률을 출력함. ./gradlew jevTest로만 실행함
 * 예: 사진 1장 + 질문 4개 → 질문마다 "예"일 확률을 찍어 보고 정답표와 비교함
 * 사진은 docs/test-data/photos/에 있음 (커밋하지 않음, 출처는 docs/test-data/2026-10-28_jev_사진_테스트.md)
 */
@Tag("jev")
@SpringBootTest(classes = {JevConfig.class, JevClient.class})
class JevPhotoLiveTest {

    private static final Path PHOTO_DIR = Path.of("../docs/test-data/photos");

    private static final List<String> PHOTOS = List.of(
            "GOGjGlLwdYQ", "-kPOROI-ZQI", "2dEoodA5x_k", "QzPNnCy_46g",
            "IH65r4HEQWQ", "bWMp9C3DFYE", "YQCUOmmu1_g", "Y3OTP6FwocA");

    private static final Map<String, String> QUESTIONS = QuestionSetV0.QUESTIONS;

    @Autowired
    JevClient jevClient;

    @Test
    void 매장_상황_사진에_질문_세트를_보낸다() throws IOException {
        System.out.println("photo | " + String.join(" | ", QUESTIONS.keySet()) + " | input_tokens | elapsed_ms");

        for (String photo : PHOTOS) {
            byte[] image = Files.readAllBytes(PHOTO_DIR.resolve(photo + ".jpg"));

            long start = System.nanoTime();
            JevResponse response = jevClient.askYesNo(image, "jpg", QUESTIONS).response();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            StringBuilder line = new StringBuilder(photo);
            for (String name : QUESTIONS.keySet()) {
                double probability = response.answers().get(name).noul();
                assertThat(probability).isBetween(0.0, 1.0);
                line.append(String.format(" | %.3f", probability));
            }
            line.append(" | ").append(response.usage().inputTokens()).append(" | ").append(elapsedMs);
            System.out.println(line);

            assertThat(response.model()).isEqualTo("jev-1.14.0");
        }
    }
}
