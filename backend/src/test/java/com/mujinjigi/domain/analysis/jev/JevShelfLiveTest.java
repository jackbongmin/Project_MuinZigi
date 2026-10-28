package com.mujinjigi.domain.analysis.jev;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 0단계: 손님이 들어오기 전(before)과 나간 뒤(after) 진열대 사진을 한 요청에 보내 상품이 사라졌는지 물음
 * 사진은 docs/test-data/shelf/에 있음 (커밋하지 않음). ./gradlew jevTest로만 실행함
 */
@Tag("jev")
@SpringBootTest(classes = JevClient.class)
class JevShelfLiveTest {

    private static final Path SHELF_DIR = Path.of("../docs/test-data/shelf");

    private static final Map<String, String> QUESTIONS = new LinkedHashMap<>();

    static {
        QUESTIONS.put("missing", "Compared with the shelf in image `before`, is any product missing from the shelf in image `after`?");
        QUESTIONS.put("same", "Do the shelves in images `before` and `after` look the same, with no products added, removed or moved?");
    }

    // 비교 이름 → [before 파일, after 파일]
    private static final Map<String, String[]> CASES = new LinkedHashMap<>();

    static {
        CASES.put("same_frame", new String[]{"P1_before.jpg", "P1_before.jpg"});
        CASES.put("p1_0s_vs_19s", new String[]{"P1_before.jpg", "P1_after.jpg"});
        CASES.put("p1_0s_vs_19s_edited", new String[]{"P1_before.jpg", "P1_after_edited.jpg"});
    }

    @Autowired
    JevClient jevClient;

    @Test
    void 진열대_전후_사진으로_사라진_상품을_묻는다() throws IOException {
        System.out.println("case | " + String.join(" | ", QUESTIONS.keySet()) + " | input_tokens | elapsed_ms");

        for (Map.Entry<String, String[]> entry : CASES.entrySet()) {
            Map<String, byte[]> images = new LinkedHashMap<>();
            images.put("before", Files.readAllBytes(SHELF_DIR.resolve(entry.getValue()[0])));
            images.put("after", Files.readAllBytes(SHELF_DIR.resolve(entry.getValue()[1])));

            long start = System.nanoTime();
            JevResponse response = jevClient.askYesNo(images, "jpg", QUESTIONS);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            StringBuilder line = new StringBuilder(entry.getKey());
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
