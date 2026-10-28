package com.mujinjigi.domain.analysis.jev;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Jev(TypeSafe) API를 HTTP로 직접 호출함. 공식 SDK가 Python, JavaScript만 있기 때문
 */
@Component
public class JevClient {

    // 요청 안에서 이미지를 가리키는 이름. state와 assets가 같은 이름으로 연결됨
    private static final String IMAGE_REF = "photo-1";

    private final RestClient restClient;
    private final String model;
    private final boolean hasApiKey;

    public JevClient(@Value("${jev.base-url}") String baseUrl,
                     @Value("${jev.api-key}") String apiKey,
                     @Value("${jev.model}") String model) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.model = model;
        this.hasApiKey = !apiKey.isBlank();
    }

    /**
     * 이미지 1장에 예/아니오 질문들을 보내고, 질문마다 "예"일 확률을 받음
     *
     * @param image     PNG 또는 JPEG 바이트 (가로·세로 280~1,568px)
     * @param format    "png", "jpg", "jpeg" 중 하나
     * @param questions 질문 이름 → 질문 문장
     */
    public JevResponse askYesNo(byte[] image, String format, Map<String, String> questions) {
        // 키가 없으면 Jev가 403만 돌려줘서 원인을 알기 어려움. 요청 전에 막고 이유를 알려줌
        if (!hasApiKey) {
            throw new IllegalStateException("JEV_API_KEY가 비어 있음. .env에 값을 넣어야 함");
        }

        Map<String, Object> asset = Map.of(
                "type", "image",
                "format", format,
                "data_base64", Base64.getEncoder().encodeToString(image));

        Map<String, Object> noulQuestions = new LinkedHashMap<>();
        questions.forEach((name, instructions) ->
                noulQuestions.put(name, Map.of("type", "noul", "instructions", instructions)));

        Map<String, Object> body = Map.of(
                "model", model,
                // 여러 질문이 같은 이미지를 보므로 state에 한 번만 넣음 (공식 권장)
                "state", Map.of("photo", IMAGE_REF),
                "assets", Map.of(IMAGE_REF, asset),
                "questions", noulQuestions);

        return restClient.post()
                .uri("/v1/systemone")
                .contentType(MediaType.APPLICATION_JSON)
                .header("TypeSafe-Danger-Zone-Images", "true")
                .body(body)
                .retrieve()
                .body(JevResponse.class);
    }
}
