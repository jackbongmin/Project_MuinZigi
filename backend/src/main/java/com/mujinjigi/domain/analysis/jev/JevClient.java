package com.mujinjigi.domain.analysis.jev;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

/**
 * 0단계 · Jev 이미지 요청 보내 보기
 * 1단계 · 04 (재시도, 요청·응답 원문 기록)
 * Jev(TypeSafe) API를 HTTP로 직접 호출함. 공식 SDK가 Python, JavaScript만 있기 때문
 */
@Component
public class JevClient {

    // 재시도 대기 시간의 상한과 무작위로 줄이는 비율 (Jev 공식 SDK 기본값과 같음)
    private static final long MAX_DELAY_MS = 5_000;
    private static final double JITTER = 0.25;

    private final RestClient restClient;
    private final String model;
    private final boolean hasApiKey;
    private final int maxRetries;
    private final long baseDelayMs;
    private final JsonMapper jsonMapper = JsonMapper.shared();

    public JevClient(RestClient jevRestClient,
                     @Value("${jev.model}") String model,
                     @Value("${jev.api-key}") String apiKey,
                     @Value("${jev.retry.max-retries}") int maxRetries,
                     @Value("${jev.retry.base-delay-ms}") long baseDelayMs) {
        this.restClient = jevRestClient;
        this.model = model;
        this.hasApiKey = !apiKey.isBlank();
        this.maxRetries = maxRetries;
        this.baseDelayMs = baseDelayMs;
    }

    /**
     * 이미지 1장에 예/아니오 질문들을 보내고, 질문마다 "예"일 확률을 받음
     *
     * @param image     PNG 또는 JPEG 바이트 (가로·세로 280~1,568px)
     * @param format    "png", "jpg", "jpeg" 중 하나
     * @param questions 질문 이름 → 질문 문장
     */
    public JevResult askYesNo(byte[] image, String format, Map<String, String> questions) {
        // 질문에서는 이 이미지를 `photo`라는 이름으로 가리킴
        return askYesNo(Map.of("photo", image), format, questions);
    }

    /**
     * 이미지 여러 장에 예/아니오 질문들을 보냄 (예: 진열대 전후 비교)
     *
     * @param images    질문에서 부를 이름 → 이미지 바이트 (예: "before", "after")
     * @param format    모든 이미지의 형식 ("png", "jpg", "jpeg")
     * @param questions 질문 이름 → 질문 문장. 문장 안에서 `before`처럼 이미지 이름으로 가리킴
     */
    public JevResult askYesNo(Map<String, byte[]> images, String format, Map<String, String> questions) {
        // 키가 없으면 Jev가 403만 돌려줘서 원인을 알기 어려움. 요청 전에 막고 이유를 알려줌
        if (!hasApiKey) {
            throw new IllegalStateException("JEV_API_KEY가 비어 있음. .env에 값을 넣어야 함");
        }

        String requestBody = jsonMapper.writeValueAsString(buildBody(images, format, questions, false));
        // 로그용 요청 원문. 이미지 base64는 프레임 한 장에 수십~수백 KB라 크기만 남김 (이미지 파일은 디스크에 있음)
        String requestLog = jsonMapper.writeValueAsString(buildBody(images, format, questions, true));

        List<JevCallAttempt> attempts = new ArrayList<>();
        for (int attempt = 1; ; attempt++) {
            LocalDateTime calledAt = LocalDateTime.now();
            long start = System.currentTimeMillis();
            try {
                ResponseEntity<String> response = restClient.post()
                        .uri("/v1/systemone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("TypeSafe-Danger-Zone-Images", "true")
                        .body(requestBody)
                        // 응답을 직접 읽음. 실패 응답(4xx·5xx)도 예외 없이 받아서 상태 코드로 재시도할지 정함
                        // 본문을 읽다가 시간 초과가 나도 연결 실패와 같은 ResourceAccessException이 됨
                        .exchange((request, res) -> ResponseEntity.status(res.getStatusCode())
                                .body(new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));

                int status = response.getStatusCode().value();
                attempts.add(new JevCallAttempt(attempt, calledAt, status, response.getBody(), null,
                        System.currentTimeMillis() - start));

                if (response.getStatusCode().is2xxSuccessful()) {
                    JevResponse jevResponse = jsonMapper.readValue(response.getBody(), JevResponse.class);
                    return new JevResult(jevResponse, requestLog, attempts);
                }
                if (!isRetryable(status) || attempt > maxRetries) {
                    throw new JevCallException("Jev 호출 실패: 상태 코드 " + status + ", " + attempt + "번 시도",
                            requestLog, attempts, null);
                }
            } catch (ResourceAccessException e) {
                // 연결 실패, 시간 초과: 응답을 끝까지 못 받음. 잠깐 끊긴 것일 수 있어서 재시도함
                attempts.add(new JevCallAttempt(attempt, calledAt, null, null, e.getMessage(),
                        System.currentTimeMillis() - start));
                if (attempt > maxRetries) {
                    throw new JevCallException("Jev 연결 실패: " + attempt + "번 시도", requestLog, attempts, e);
                }
            }
            waitBeforeRetry(attempt);
        }
    }

    // 408(시간 초과), 429(요청 한도 초과), 5xx(529 과부하 포함)는 잠시 뒤 다시 보내면 성공할 수 있음
    // 400·401·403·422처럼 요청이 잘못된 경우는 다시 보내도 똑같이 실패하므로 재시도하지 않음
    private boolean isRetryable(int status) {
        return status == 408 || status == 429 || status >= 500;
    }

    // 지수 백오프: 기다리는 시간을 0.5초 → 1초 → 2초처럼 두 배씩 늘림 (최대 5초)
    // 지터: 여러 요청이 동시에 실패했을 때 같은 순간에 다시 몰리지 않도록 최대 25%를 무작위로 줄임
    private void waitBeforeRetry(int attempt) {
        long delay = Math.min(baseDelayMs * (1L << (attempt - 1)), MAX_DELAY_MS);
        long jittered = (long) (delay * (1 - JITTER * Math.random()));
        try {
            Thread.sleep(jittered);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Jev 재시도 대기 중 중단됨", e);
        }
    }

    // state에는 이름 → 참조 문자열, assets에는 참조 문자열 → 이미지. 같은 참조 문자열로 연결됨
    private Map<String, Object> buildBody(Map<String, byte[]> images, String format, Map<String, String> questions,
                                          boolean forLog) {
        Map<String, Object> state = new LinkedHashMap<>();
        Map<String, Object> assets = new LinkedHashMap<>();
        images.forEach((name, image) -> {
            String ref = "image-" + name;
            state.put(name, ref);
            String data = forLog
                    ? "(생략: " + image.length + " bytes)"
                    : Base64.getEncoder().encodeToString(image);
            assets.put(ref, Map.of("type", "image", "format", format, "data_base64", data));
        });

        Map<String, Object> noulQuestions = new LinkedHashMap<>();
        questions.forEach((name, instructions) ->
                noulQuestions.put(name, Map.of("type", "noul", "instructions", instructions)));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        // 여러 질문이 같은 이미지를 보므로 state에 한 번만 넣음 (공식 권장)
        body.put("state", state);
        body.put("assets", assets);
        body.put("questions", noulQuestions);
        return body;
    }
}