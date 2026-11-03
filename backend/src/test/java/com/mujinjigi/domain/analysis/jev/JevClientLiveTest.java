package com.mujinjigi.domain.analysis.jev;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 0단계 · Jev 이미지 요청 보내 보기
 * 실제 Jev API를 호출하는 테스트. 비용과 네트워크가 필요해서 ./gradlew jevTest로만 실행함
 * JevClient만 띄워서 DB 없이도 실행됨
 */
@Tag("jev")
@SpringBootTest(classes = {JevConfig.class, JevClient.class})
class JevClientLiveTest {

    @Autowired
    JevClient jevClient;

    @Test
    void 직접_그린_이미지로_예아니오_질문을_보낸다() throws IOException {
        byte[] png = drawRedSquareOnWhite();
        Map<String, String> questions = new LinkedHashMap<>();
        questions.put("red_square", "Is there a red square in the image `photo`?");
        questions.put("blue_circle", "Is there a blue circle in the image `photo`?");

        long start = System.nanoTime();
        JevResponse response = jevClient.askYesNo(png, "png", questions).response();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.printf("model=%s, red_square=%.3f, blue_circle=%.3f, input_tokens=%d, elapsed=%dms%n",
                response.model(),
                response.answers().get("red_square").noul(),
                response.answers().get("blue_circle").noul(),
                response.usage().inputTokens(),
                elapsedMs);

        assertThat(response.model()).isEqualTo("jev-1.14.0");
        assertThat(response.answers().get("red_square").noul()).isGreaterThan(0.5);
        assertThat(response.answers().get("blue_circle").noul()).isLessThan(0.5);
    }

    // 흰 바탕(400x400)에 빨간 사각형 하나. 정답이 분명한 이미지로 호출 자체가 되는지 확인함
    private byte[] drawRedSquareOnWhite() throws IOException {
        BufferedImage image = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 400, 400);
        g.setColor(Color.RED);
        g.fillRect(120, 120, 160, 160);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
