package com.mujinjigi.domain.analysis.jev;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 0단계: 매장 상황 사진에 질문 세트를 보내고 확률을 출력함. ./gradlew jevTest로만 실행함
 * 사진은 docs/test-data/photos/에 있음 (커밋하지 않음, 출처는 docs/test-data/2026-10-28_jev_사진_테스트.md)
 */
@Tag("jev")
@SpringBootTest(classes = JevClient.class)
class JevPhotoLiveTest {

    private static final Path PHOTO_DIR = Path.of("../docs/test-data/photos");

    private static final List<String> PHOTOS = List.of(
            "GOGjGlLwdYQ", "-kPOROI-ZQI", "2dEoodA5x_k", "QzPNnCy_46g",
            "IH65r4HEQWQ", "bWMp9C3DFYE", "YQCUOmmu1_g", "Y3OTP6FwocA");

    // 질문 세트 v0: 복잡한 질문 하나 대신 단순한 예/아니오 질문 여러 개로 나눔 (TypeSafe 권고)
    private static final Map<String, String> QUESTIONS = new LinkedHashMap<>();

    static {
        QUESTIONS.put("person", "Is there a person or part of a person (such as a hand) in the image `photo`?");
        QUESTIONS.put("holding_product", "Is a person holding a store product (not a shopping bag) in their hand in the image `photo`?");
        QUESTIONS.put("putting_in_bag", "Is a person putting an item into a bag or pocket in the image `photo`?");
        QUESTIONS.put("paying", "Is a person paying (by card, phone or cash) at a checkout or payment terminal in the image `photo`?");
    }

    @Autowired
    JevClient jevClient;

    @Test
    void 매장_상황_사진에_질문_세트를_보낸다() throws IOException {
        System.out.println("photo | " + String.join(" | ", QUESTIONS.keySet()) + " | input_tokens | elapsed_ms");

        for (String photo : PHOTOS) {
            byte[] image = Files.readAllBytes(PHOTO_DIR.resolve(photo + ".jpg"));

            long start = System.nanoTime();
            JevResponse response = jevClient.askYesNo(image, "jpg", QUESTIONS);
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
