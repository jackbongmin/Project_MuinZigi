package com.mujinjigi.domain.analysis.jev;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 0단계: 영상에서 뽑은 프레임에 질문 세트를 보내고 확률을 출력함. ./gradlew jevTest로만 실행함
 * 프레임은 docs/test-data/frames/<장면 코드>/에 있음 (FFmpeg로 1초에 1장, 커밋하지 않음)
 */
@Tag("jev")
@SpringBootTest(classes = JevClient.class)
class JevFrameLiveTest {

    private static final Path FRAME_ROOT = Path.of("../docs/test-data/frames");
    private static final Map<String, String> QUESTIONS = QuestionSetV0.QUESTIONS;

    @Autowired
    JevClient jevClient;

    @Test
    void 장면별_프레임에_질문_세트를_보낸다() throws IOException {
        List<Path> scenes;
        try (Stream<Path> dirs = Files.list(FRAME_ROOT)) {
            scenes = dirs.filter(Files::isDirectory).sorted().toList();
        }
        assertThat(scenes).as("frames 폴더에 장면 폴더가 없음").isNotEmpty();

        System.out.println("frame | " + String.join(" | ", QUESTIONS.keySet()) + " | input_tokens | elapsed_ms");

        for (Path scene : scenes) {
            List<Path> frames;
            try (Stream<Path> files = Files.list(scene)) {
                frames = files.filter(f -> f.toString().endsWith(".jpg")).sorted().toList();
            }

            for (Path frame : frames) {
                long start = System.nanoTime();
                JevResponse response = jevClient.askYesNo(Files.readAllBytes(frame), "jpg", QUESTIONS);
                long elapsedMs = (System.nanoTime() - start) / 1_000_000;

                StringBuilder line = new StringBuilder(frame.getFileName().toString());
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
}
