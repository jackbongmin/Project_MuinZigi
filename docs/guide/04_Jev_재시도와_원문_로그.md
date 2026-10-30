# 가이드 04 · Jev 재시도와 원문 로그

> **한 줄 요약**: **Jev 호출**(`JevClient`)이 시간 초과·한도 초과·과부하로 실패하면 **잠깐 기다렸다 다시 보내고**, 시도마다 보낸 요청과 받은 응답 원문을 **프레임과 연결해 DB(`jev_call_logs`)**에 남기는 기능 구현임

## 목표
- Jev 호출이 잠깐 실패(429 한도 초과, 529 과부하, 시간 초과)해도 판단 요청 전체가 멈추지 않게 함. 최대 2번 다시 보냄
- 시도마다 상태 코드, 요청·응답 원문, 걸린 시간, 보낸 시각을 DB에 남김. 판단이 이상할 때 Jev가 실제로 무엇을 받고 답했는지 확인할 수 있음
- 재시도까지 실패하면 500이 아니라 502(외부 서버 문제)를 돌려줌
- 기본 테스트에서 한 번도 안 돌던 `JevClient`의 요청 만들기를 가짜 서버로 확인함 (1단계 "테스트 보강"의 `JevClient` 부분)
- 1단계 항목 "호출 실패 시 재시도, 요청·응답 원문 로그 저장"에 해당함. 결정 005

## 완료 기준
- `.\gradlew.bat test` 통과 (새 테스트 5개 포함, 전체 22개)
- 서버를 띄우고 P1을 판단하면 `jev_call_logs`에 16줄이 남음
- 응답 시간 제한을 1ms로 줄여 서버를 띄우면 3번 시도 후 `502`가 오고 기록이 3줄 남음. 원래 설정으로 다시 부르면 이어서 16개를 판단함

## 바뀌는 API
API 주소는 그대로이고, 실패할 때의 응답만 바뀜.

| 요청 | 바뀌는 점 |
|---|---|
| `POST /api/videos/{videoId}/judgments` | 재시도까지 실패하면 `502 Bad Gateway` (전에는 500). 실패 전까지 판단한 프레임은 저장돼 있어서 다시 부르면 이어서 함 |

---

## 1. 개념 먼저

### 1-1. 이번에 바꾸는 흐름
```
JudgmentService.judgeOne(프레임)
  └─ JevClient.askYesNo(이미지, 질문)
       ├─ 1번째 시도 → 529 과부하      ─┐
       │    (0.5초 기다림)               │ 시도마다 JevCallAttempt 하나
       ├─ 2번째 시도 → 시간 초과        │ (상태 코드, 응답 원문, 오류, 걸린 시간, 보낸 시각)
       │    (1초 기다림)                 │
       └─ 3번째 시도 → 200 성공        ─┘
            → JevResult(응답, 요청 원문, 시도 기록 3개) 반환
  └─ 시도 기록 3개를 jev_call_logs에 저장 → 판단 결과를 frame_judgments에 저장

3번 다 실패하면 → JevCallException(요청 원문, 시도 기록 3개)
  └─ JudgmentService가 시도 기록을 저장하고 502를 돌려줌
```

### 1-2. 어떤 실패를 다시 보내나
다시 보내서 **성공할 가능성이 있는 실패**만 재시도함. 기준은 Jev 공식 문서와 공식 SDK 기본값임.

| 실패 | 의미 | 재시도 |
|---|---|---|
| 429 Too Many Requests | 요청 한도 초과. 조금 뒤엔 풀림 | O |
| 529 Overloaded (5xx) | Jev 서버가 잠깐 바쁨 | O |
| 408, 그 밖의 5xx | 서버 쪽 시간 초과·오류 | O |
| 연결 실패, 시간 초과 | 응답을 끝까지 못 받음 | O |
| 400, 401, 403, 422 | **우리 요청이 잘못됨** (형식 오류, 키 없음) | X. 다시 보내도 똑같이 실패함 |

- 재시도에도 비용이 듦. 잘못된 요청을 세 번 보내면 시간만 세 배로 걸림

### 1-3. 지수 백오프와 지터
- **바로 다시 보내면 안 됨**: 서버가 바빠서 실패했는데 즉시 다시 보내면 더 바빠짐
- **지수 백오프(exponential backoff)**: 기다리는 시간을 0.5초 → 1초 → 2초처럼 **두 배씩** 늘림. 최대 5초에서 멈춤
- **지터(jitter)**: 기다리는 시간을 매번 최대 25%까지 **무작위로 줄임**
  - 여러 요청이 같은 순간에 실패하면, 지터가 없을 때 정확히 같은 순간에 다시 몰림. 무작위로 흩어 주면 몰림이 줄어듦
- 계산: `대기 = min(0.5초 × 2^(시도-1), 5초) × (1 - 0.25 × 무작위 0~1)`

### 1-4. 시간 제한(timeout)이 있어야 재시도를 할 수 있음
- 시간 제한이 없으면 Jev가 응답하지 않을 때 요청이 **끝없이 기다림**. 실패로 끝나지 않으니 재시도할 기회도 없음
- 연결 5초, 응답 30초로 둠. 평소 응답은 프레임 1장에 0.3초 정도임
- 시간 제한은 HTTP 클라이언트(`RestClient`)를 만들 때 정함. 그래서 `RestClient`를 만드는 일을 **설정 클래스(`JevConfig`)로 뺌**
  - 빼면 테스트에서 진짜 `RestClient` 대신 **가짜 서버에 연결된 `RestClient`**를 넣을 수 있음 (1-8)
  - `@Configuration` 클래스의 `@Bean` 메서드가 돌려준 객체를 스프링이 보관하고, `RestClient`가 필요한 곳(`JevClient` 생성자)에 넣어 줌

### 1-5. `exchange()`로 응답을 직접 읽기
- 전에는 `.retrieve().body(JevResponse.class)`를 씀. 이 방식은 4xx·5xx를 받으면 **예외를 던짐**. 상태 코드를 보고 재시도할지 정하려면 불편함
- `exchange((request, res) -> ...)`는 **상태 코드와 본문을 직접 읽음**. 4xx·5xx도 예외 없이 받음
- 본문을 읽다가 시간 초과가 나면 연결 실패와 똑같이 `ResourceAccessException`이 됨. 그래서 `catch` 하나로 "응답을 끝까지 못 받은 경우"를 모두 잡음
  - 처음엔 `retrieve().onStatus(...).toEntity(String.class)`로 만들었음. 그런데 본문을 읽다 난 시간 초과는 `ResourceAccessException`이 아닌 `RestClientException`이 되어 재시도되지 않았음. 실제 서버에서 찾은 문제임 → 트러블슈팅 `2026-10-30_본문_읽기_시간_초과가_재시도되지_않음.md`
- 본문을 문자열로 받은 뒤 `JsonMapper`로 `JevResponse`를 만듦. 원문 문자열을 기록에 남겨야 해서 이렇게 함

### 1-6. 시도 기록을 담는 세 가지 타입
| 타입 | 언제 | 담는 것 |
|---|---|---|
| `JevCallAttempt` (record) | 시도 한 번마다 | 몇 번째, 보낸 시각, 상태 코드, 응답 원문, 오류, 걸린 시간 |
| `JevResult` (record) | 성공했을 때 반환 | 읽은 응답 + 요청 원문 + 시도 기록 목록 |
| `JevCallException` (예외) | 재시도까지 실패했을 때 던짐 | 요청 원문 + 시도 기록 목록 |

- 성공이든 실패든 **시도 기록을 호출한 쪽에 돌려줘야** 저장할 수 있음. 성공은 반환값, 실패는 예외에 담음
- `JevClient`는 DB를 모름. 기록을 DB에 저장하는 일은 프레임을 아는 `JudgmentService`가 함

### 1-7. 502 Bad Gateway와 이어서 하기
- 500(Internal Server Error): 우리 서버 문제
- **502(Bad Gateway)**: 우리 서버가 의존하는 **외부 서버(Jev)가 제대로 답하지 않음**. 화면에서 "잠시 후 다시 시도" 안내를 하기에 맞는 코드임
- 가이드 03의 "이미 판단한 프레임은 건너뜀" 덕분에, 502 뒤에 다시 부르면 실패한 프레임부터 이어서 함. 재시도에 **멱등성**이 중요한 이유임

### 1-8. 가짜 서버(`MockRestServiceServer`)와 가짜 객체(`@MockitoBean`)
| | `@MockitoBean JevClient` (가이드 03) | `MockRestServiceServer` (이번) |
|---|---|---|
| 무엇을 가짜로 | `JevClient` 객체 전체 | Jev **HTTP 서버**만 |
| 실행되는 우리 코드 | `JevClient`는 안 돌고 서비스·컨트롤러만 | `JevClient`의 요청 만들기, 재시도, 응답 읽기가 진짜로 돎 |
| 확인하는 것 | 서비스가 결과·기록을 맞게 저장하나 | 요청 본문이 맞나, 어떤 실패를 몇 번 재시도하나 |

- 가짜 서버 사용법
  - `MockRestServiceServer.bindTo(builder)`: 이 `RestClient.Builder`로 만든 클라이언트의 요청을 가짜 서버가 받음
  - `server.expect(requestTo(URL)).andExpect(jsonPath(...))`: 이런 요청이 와야 함
  - `.andRespond(withSuccess(...))`, `withTooManyRequests()`, `withStatus(529)`, `withException(...)`: 이렇게 답함
  - `server.verify()`: 기대한 요청이 모두 왔는지 확인함
- 가짜 서버도 **내가 상상한 실패만** 흉내 냄. 그래서 4-2에서 실제 서버에 일부러 시간 초과를 만들어 확인함

---

## 2. 미리 바꿔둔 설정

### `application.properties`
```properties
# 연결·응답 시간 제한(밀리초). 넘으면 끊고 재시도함. 평소 응답은 프레임 1장에 0.3초 정도임
jev.connect-timeout-ms=5000
jev.read-timeout-ms=30000
# 실패 시 재시도 횟수와 첫 대기 시간. 대기 시간은 두 배씩 늘어남 (0.5초 → 1초). Jev 공식 SDK 기본값과 같음
jev.retry.max-retries=2
jev.retry.base-delay-ms=500
```
- 시간 제한과 재시도 값을 코드가 아닌 설정에 둔 이유: 서버를 띄울 때 `--jev.read-timeout-ms=1`처럼 바꿔서 실패를 만들어 볼 수 있음 (4-2)
- `jev_call_logs` 테이블은 `ddl-auto=update`가 서버를 띄울 때 자동으로 만듦

### 0단계 실제 호출 테스트 4개 (`JevClientLiveTest` 등)
- `JevClient`의 반환 타입이 `JevResponse`에서 `JevResult`로 바뀌고, `RestClient`를 `JevConfig`에서 받게 됨
- 실험용 테스트라 Claude Code가 리뷰 때 맞춰 고침. 지금은 손대지 않아도 됨

---

## 3. 코드 작성
- 작업 브랜치: `feature/jev-retry-log`
- 새 파일의 빈 파일(테스트 포함)은 Claude Code가 미리 만들어 둠. 선언 줄까지 아래 코드로 통째로 바꾸면 됨
- **기존 파일 3개(`JevClient`, `JudgmentService`, `JudgmentControllerTest`)는 아래 코드로 전체를 바꿈**. 바뀐 곳은 각 절 설명에 적음

```
domain/analysis/
├── jev/JevConfig.java                        (새로 만듦)
├── jev/JevCallAttempt.java                   (새로 만듦)
├── jev/JevResult.java                        (새로 만듦)
├── jev/JevCallException.java                 (새로 만듦)
├── jev/JevClient.java                        (전체 바꿈)
├── entity/JevCallLog.java                    (새로 만듦)
├── repository/JevCallLogRepository.java      (새로 만듦)
└── service/JudgmentService.java              (전체 바꿈)
test/.../domain/analysis/
├── jev/JevClientTest.java                    (새로 만듦)
└── controller/JudgmentControllerTest.java    (전체 바꿈)
```

### 3-1. `analysis/jev/JevConfig.java`
```java
package com.mujinjigi.domain.analysis.jev;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 1단계 · 04 (Jev 재시도와 원문 로그)
 * Jev에 요청을 보낼 RestClient를 만듦. 주소·인증·시간 제한을 여기서 한 번만 정함
 * JevClient 밖에서 만들어 넘기므로, 테스트에서는 가짜 서버에 연결된 RestClient를 넣을 수 있음
 */
@Configuration
public class JevConfig {

    @Bean
    public RestClient jevRestClient(@Value("${jev.base-url}") String baseUrl,
                                    @Value("${jev.api-key}") String apiKey,
                                    @Value("${jev.connect-timeout-ms}") long connectTimeoutMs,
                                    @Value("${jev.read-timeout-ms}") long readTimeoutMs) {
        // 시간 제한이 없으면 Jev가 응답하지 않을 때 요청이 끝없이 기다림. 끊겨야 재시도할 수 있음
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .requestFactory(requestFactory)
                .build();
    }
}
```
- `@Configuration` + `@Bean`: 이 메서드가 돌려준 `RestClient`를 스프링이 보관하고, 필요한 곳에 넣어 줌
- 주소·인증 헤더는 전에 `JevClient` 생성자에 있던 코드를 그대로 옮김. 새로 더한 것은 시간 제한 두 줄임
- `SimpleClientHttpRequestFactory`: 자바 기본 HTTP 연결(`HttpURLConnection`)로 요청을 보내는 공장. 연결·응답 시간 제한을 정할 수 있음

### 3-2. `analysis/jev/JevCallAttempt.java`
```java
package com.mujinjigi.domain.analysis.jev;

import java.time.LocalDateTime;

/**
 * 1단계 · 04 (Jev 재시도와 원문 로그)
 * Jev 호출 한 번(시도 한 번)의 결과. 재시도하면 시도마다 하나씩 생김
 *
 * @param attempt      몇 번째 시도인지 (1부터)
 * @param calledAt     이 시도를 보낸 시각. 재시도 사이에 얼마나 기다렸는지 볼 수 있음
 * @param statusCode   HTTP 상태 코드. 연결 실패·시간 초과로 응답을 못 받았으면 null
 * @param responseBody 응답 본문 원문. 응답을 못 받았으면 null
 * @param error        응답을 못 받은 이유 (연결 실패, 시간 초과). 응답을 받았으면 null
 * @param elapsedMs    이 시도에 걸린 시간(밀리초)
 */
public record JevCallAttempt(int attempt, LocalDateTime calledAt, Integer statusCode, String responseBody,
                             String error, long elapsedMs) {
}
```
- `statusCode`가 `int`가 아니라 `Integer`인 이유: 시간 초과면 상태 코드가 **없음**. `int`는 null을 담을 수 없음
- `calledAt`(보낸 시각)과 `elapsedMs`(걸린 시간)로 재시도 사이에 얼마나 기다렸는지 계산할 수 있음 (4-2)

### 3-3. `analysis/jev/JevResult.java`
```java
package com.mujinjigi.domain.analysis.jev;

import java.util.List;

/**
 * 1단계 · 04 (Jev 재시도와 원문 로그)
 * Jev 호출 성공 결과. 읽은 응답과 함께, 로그로 남길 요청 원문과 시도 기록을 담음
 *
 * @param response    읽은 응답
 * @param requestBody 보낸 요청 본문 원문 (이미지 base64는 크기만 남기고 뺌)
 * @param attempts    시도 기록. 마지막 시도가 성공한 시도임
 */
public record JevResult(JevResponse response, String requestBody, List<JevCallAttempt> attempts) {
}
```

### 3-4. `analysis/jev/JevCallException.java`
```java
package com.mujinjigi.domain.analysis.jev;

import java.util.List;

/**
 * 1단계 · 04 (Jev 재시도와 원문 로그)
 * 재시도까지 했는데도 Jev 호출이 실패함. 실패한 시도 기록도 로그로 남길 수 있게 같이 담음
 */
public class JevCallException extends RuntimeException {

    private final String requestBody;
    private final List<JevCallAttempt> attempts;

    public JevCallException(String message, String requestBody, List<JevCallAttempt> attempts, Throwable cause) {
        super(message, cause);
        this.requestBody = requestBody;
        this.attempts = attempts;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public List<JevCallAttempt> getAttempts() {
        return attempts;
    }
}
```
- `RuntimeException`을 상속함. 메서드마다 `throws`를 적지 않아도 됨 (`ResponseStatusException`과 같은 방식)
- `cause`: 원래 예외(시간 초과 등)를 같이 담아 둠. 로그에서 `Caused by`로 진짜 원인을 볼 수 있음

### 3-5. `analysis/jev/JevClient.java` (전체 바꿈)
```java
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
```
바뀐 곳:
- 생성자: `RestClient`를 직접 만들지 않고 `JevConfig`가 만든 것을 받음. 재시도 설정 두 개를 받음
- 반환 타입: `JevResponse` → `JevResult` (응답 + 요청 원문 + 시도 기록)
- `for (int attempt = 1; ; attempt++)`: 끝 조건이 없는 반복문. 성공하면 `return`, 더 시도할 수 없으면 `throw`로 빠져나감
- `attempt > maxRetries`: 첫 시도는 재시도가 아님. `maxRetries=2`면 1·2·3번째까지 보내고, 3번째가 실패하면 `3 > 2`라서 멈춤
- `1L << (attempt - 1)`: 2의 (attempt-1)제곱. 1번째 실패 후 ×1, 2번째 후 ×2, 3번째 후 ×4
- `Thread.sleep` 중 `InterruptedException`: 서버가 꺼질 때 대기 중인 스레드를 깨우면 남. 중단 표시를 다시 세우고(`interrupt()`) 멈춤
- `buildBody(..., forLog)`: 같은 본문을 두 번 만듦. 보낼 것은 이미지 포함, 기록할 것은 이미지 대신 `(생략: N bytes)`
- `JsonMapper.shared()`: Jackson 3의 기본 설정 JSON 변환기. 본문을 직접 문자열로 만들어야 기록과 실제 요청이 같은 내용이 됨

### 3-6. `analysis/entity/JevCallLog.java`
```java
package com.mujinjigi.domain.analysis.entity;

import java.time.LocalDateTime;

import com.mujinjigi.domain.analysis.jev.JevCallAttempt;
import com.mujinjigi.domain.video.entity.VideoFrame;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 1단계 · 04 (Jev 재시도와 원문 로그)
 * Jev 호출 시도 한 번의 기록. 성공·실패와 상관없이 시도마다 한 줄씩 남김
 * 판단이 이상할 때 Jev가 실제로 무엇을 받고 무엇을 답했는지 원문으로 확인하려고 둠
 * 아직 읽는 곳이 없어서 getter는 두지 않음 (측정 로그에서 읽을 때 추가)
 */
@Entity
@Table(name = "jev_call_logs")
public class JevCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어떤 프레임을 판단하다 생긴 기록인지
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "frame_id")
    private VideoFrame frame;

    // 몇 번째 시도인지 (1부터). 2 이상이면 재시도한 것임
    @Column(nullable = false)
    private int attempt;

    // HTTP 상태 코드. 연결 실패·시간 초과면 응답이 없어서 비어 있음
    private Integer statusCode;

    @Column(nullable = false)
    private long elapsedMs;

    // 보낸 요청 원문 (이미지 base64는 크기만 남김). 길이 제한이 없는 text로 둠
    @Column(nullable = false, columnDefinition = "text")
    private String requestBody;

    // 받은 응답 원문. 오류 응답은 JSON이 아닐 수도 있어서 jsonb가 아니라 text로 둠
    @Column(columnDefinition = "text")
    private String responseBody;

    // 응답을 못 받은 이유 (연결 실패, 시간 초과)
    @Column(columnDefinition = "text")
    private String error;

    // 이 시도를 보낸 시각. 재시도 간격(0.5초 → 1초)을 확인할 수 있음
    @Column(nullable = false)
    private LocalDateTime calledAt;

    protected JevCallLog() {
    }

    public JevCallLog(VideoFrame frame, String requestBody, JevCallAttempt attempt) {
        this.frame = frame;
        this.attempt = attempt.attempt();
        this.statusCode = attempt.statusCode();
        this.elapsedMs = attempt.elapsedMs();
        this.requestBody = requestBody;
        this.responseBody = attempt.responseBody();
        this.error = attempt.error();
        this.calledAt = attempt.calledAt();
    }
}
```
- `columnDefinition = "text"`: PostgreSQL의 길이 제한 없는 문자열. `String` 기본(`varchar(255)`)으로는 요청 원문(약 800자)이 안 들어감
- 응답 원문을 `jsonb`가 아니라 `text`로 둔 이유: 오류 응답은 JSON이 아닐 수 있음(`overloaded` 같은 글자). `jsonb`는 JSON이 아니면 저장을 거부함
- 생성자가 `JevCallAttempt`를 통째로 받음. 시도 기록의 칸을 하나씩 넘기면 인자가 8개가 되어 순서를 틀리기 쉬움

### 3-7. `analysis/repository/JevCallLogRepository.java`
```java
package com.mujinjigi.domain.analysis.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mujinjigi.domain.analysis.entity.JevCallLog;

/**
 * 1단계 · 04 (Jev 재시도와 원문 로그)
 * JevCallLog 저장·조회
 */
public interface JevCallLogRepository extends JpaRepository<JevCallLog, Long> {

    // 영상 하나에서 남긴 시도 기록 수 (frame.video.id 기준). 메서드 이름 규칙으로 쿼리를 만듦
    long countByFrameVideoId(Long videoId);
}
```
- `countByFrameVideoId`: `frame` → `video` → `id`를 따라가는 메서드 이름 규칙 (가이드 02의 `findByVideoIdOrderByTimeSecAsc`와 같은 방식). 테스트에서 영상 하나의 기록 수를 셀 때 씀

### 3-8. `analysis/service/JudgmentService.java` (전체 바꿈)
```java
package com.mujinjigi.domain.analysis.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.mujinjigi.domain.analysis.dto.FrameJudgmentResponse;
import com.mujinjigi.domain.analysis.entity.FrameJudgment;
import com.mujinjigi.domain.analysis.entity.JevCallLog;
import com.mujinjigi.domain.analysis.jev.FrameQuestions;
import com.mujinjigi.domain.analysis.jev.JevCallAttempt;
import com.mujinjigi.domain.analysis.jev.JevCallException;
import com.mujinjigi.domain.analysis.jev.JevClient;
import com.mujinjigi.domain.analysis.jev.JevResponse;
import com.mujinjigi.domain.analysis.jev.JevResult;
import com.mujinjigi.domain.analysis.repository.FrameJudgmentRepository;
import com.mujinjigi.domain.analysis.repository.JevCallLogRepository;
import com.mujinjigi.domain.video.entity.VideoFrame;
import com.mujinjigi.domain.video.repository.VideoFrameRepository;
import com.mujinjigi.domain.video.repository.VideoRepository;
import com.mujinjigi.domain.video.service.FrameService;


/**
 * 1단계 · 03 (Jev 프레임 판단)
 * 1단계 · 04 (Jev 호출 기록 저장, 실패 시 502)
 * 저장된 프레임을 Jev에 보내 판단 결과를 저장함
 */
@Service
public class JudgmentService {

    private final VideoRepository videoRepository;
    private final VideoFrameRepository videoFrameRepository;
    private final FrameJudgmentRepository frameJudgmentRepository;
    private final JevCallLogRepository jevCallLogRepository;
    private final FrameService frameService;
    private final JevClient jevClient;

    public JudgmentService(VideoRepository videoRepository,
                           VideoFrameRepository videoFrameRepository,
                           FrameJudgmentRepository frameJudgmentRepository,
                           JevCallLogRepository jevCallLogRepository,
                           FrameService frameService,
                           JevClient jevClient) {
        this.videoRepository = videoRepository;
        this.videoFrameRepository = videoFrameRepository;
        this.frameJudgmentRepository = frameJudgmentRepository;
        this.jevCallLogRepository = jevCallLogRepository;
        this.frameService = frameService;
        this.jevClient = jevClient;
    }

    // @Transactional을 붙히지 안ㅇ흠.
    // Jev 호출(프레임당 약 1초)을 하는 동안 DB 연결을 붙잡지 않으려고 프레임 하나를 판단할 때마다 save()로 바로 저장함
    // (save()는 그 자체로 짧은 트랜잭션)
    public List<FrameJudgmentResponse> judge(Long videoId) {
        checkVideoExists(videoId);
        List<VideoFrame> frames = videoFrameRepository.findByVideoIdOrderByTimeSecAsc(videoId);
        if(frames.isEmpty()){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "프레임을 먼저 추출해야 함: " + videoId);
        }

        Set<Long> judgedFrameIds = frameJudgmentRepository.findAllWithFrame(videoId, FrameQuestions.VERSION).stream()
                .map(judgment -> judgment.getFrame().getId())
                .collect(Collectors.toSet());

        for (VideoFrame frame : frames) {
            if (!judgedFrameIds.contains(frame.getId())) {
                frameJudgmentRepository.save(judgeOne(videoId, frame));
            }
        }
        return findAll(videoId);
    }

    public List<FrameJudgmentResponse> findAll(Long videoId) {
        checkVideoExists(videoId);
        return frameJudgmentRepository.findAllWithFrame(videoId, FrameQuestions.VERSION).stream()
                .map(FrameJudgmentResponse::from)
                .toList();
    }


    private FrameJudgment judgeOne(Long videoId, VideoFrame frame) {
        byte[] image = readImage(videoId, frame);
        JevResult result;
        try {
            result = jevClient.askYesNo(image, "jpg", FrameQuestions.QUESTIONS);
        } catch (JevCallException e) {
            // 재시도까지 실패함. 실패한 시도도 기록으로 남기고, 외부 서버 문제라는 뜻으로 502를 돌려줌
            // 이미 판단한 프레임은 저장돼 있어서, 다시 부르면 남은 프레임부터 이어서 판단함
            saveCallLogs(frame, e.getRequestBody(), e.getAttempts());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    e.getMessage() + " (프레임 " + frame.getTimeSec() + "초)", e);
        }
        saveCallLogs(frame, result.requestBody(), result.attempts());
        JevResponse response = result.response();

        // 응답 형식(Answer 객체)을 그대로 저장하지 않고, 질문 이름 → 확률만 꺼내서 저장함
        Map<String, Double> answers = new LinkedHashMap<>();
        response.answers().forEach((name, answer) -> answers.put(name, answer.noul()));

        return new FrameJudgment(frame, FrameQuestions.VERSION, response.model(),
                response.usage().inputTokens(), answers);
    }


    // 시도마다 한 줄씩 저장함 (재시도했으면 여러 줄)
    private void saveCallLogs(VideoFrame frame, String requestBody, List<JevCallAttempt> attempts) {
        jevCallLogRepository.saveAll(attempts.stream()
                .map(attempt -> new JevCallLog(frame, requestBody, attempt))
                .toList());
    }

    private byte[] readImage(Long videoId, VideoFrame frame) {
        try {
            return Files.readAllBytes(frameService.imagePath(videoId, frame.getFileName()));
        } catch (IOException e) {
            throw new UncheckedIOException("프레임 이미지를 읽지 못함: " + frame.getFileName(), e);
        }
    }

    private void checkVideoExists(Long videoId) {
        if (!videoRepository.existsById(videoId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "영상이 없음: " + videoId);
        }
    }
}
```
바뀐 곳:
- 필드·생성자에 `JevCallLogRepository` 추가
- `judgeOne`: 성공하면 `result.attempts()`, 실패하면 `e.getAttempts()`를 저장함. 실패해도 기록을 먼저 저장한 뒤 502를 던짐
- `saveCallLogs`: `saveAll`로 시도 기록 여러 개를 한 번에 저장함. 이 메서드에도 `@Transactional`이 없음. `saveAll` 자체가 짧은 트랜잭션임 (가이드 03 1-5와 같은 이유)
- `new ResponseStatusException(HttpStatus.BAD_GATEWAY, ..., e)`: 마지막 인자로 원래 예외를 넘겨 로그에 원인이 남게 함

### 3-9. 테스트: `analysis/jev/JevClientTest.java`
```java
package com.mujinjigi.domain.analysis.jev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 1단계 · 04 (Jev 재시도와 원문 로그)
 * JevClient 테스트. 진짜 Jev 대신 가짜 서버(MockRestServiceServer)가 정해 둔 응답을 돌려줌
 * Spring 없이 객체를 직접 만들어서 빠르게 확인함
 */
class JevClientTest {

    private static final String URL = "https://jev.test/v1/systemone";
    private static final Map<String, String> QUESTIONS = Map.of("person", "Is there a person in the image `photo`?");
    private static final String OK_BODY = """
            {"model": "jev-1.14.0",
             "answers": {"person": {"type": "noul", "noul": 0.9}},
             "usage": {"input_tokens": 2803, "output_tokens": 0}}
            """;
    private static final byte[] IMAGE = {1, 2, 3};

    MockRestServiceServer server;
    JevClient jevClient;

    @BeforeEach
    void setUp() {
        // 가짜 서버에 연결된 RestClient를 만들어 넣음. 재시도 대기 시간은 0으로 둬서 테스트가 바로 끝나게 함
        RestClient.Builder builder = RestClient.builder().baseUrl("https://jev.test");
        server = MockRestServiceServer.bindTo(builder).build();
        jevClient = new JevClient(builder.build(), "jev-1.14.0", "test-key", 2, 0);
    }

    @Test
    void 요청을_만들어_보내고_응답을_읽는다() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("TypeSafe-Danger-Zone-Images", "true"))
                .andExpect(jsonPath("$.model").value("jev-1.14.0"))
                .andExpect(jsonPath("$.state.photo").value("image-photo"))
                .andExpect(jsonPath("$.assets['image-photo'].format").value("jpg"))
                .andExpect(jsonPath("$.assets['image-photo'].data_base64").value(Base64.getEncoder().encodeToString(IMAGE)))
                .andExpect(jsonPath("$.questions.person.type").value("noul"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        JevResult result = jevClient.askYesNo(IMAGE, "jpg", QUESTIONS);

        assertThat(result.response().answers().get("person").noul()).isEqualTo(0.9);
        assertThat(result.response().usage().inputTokens()).isEqualTo(2803);
        assertThat(result.attempts()).hasSize(1);
        // 로그용 요청 원문에는 이미지 대신 크기만 들어감
        assertThat(result.requestBody()).contains("(생략: 3 bytes)").contains("person");
        server.verify();
    }

    @Test
    void 시간_초과는_재시도해서_성공한다() {
        // 연결하다가 시간 초과
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("Connect timed out")));
        // 응답은 왔는데 본문을 읽다가 시간 초과 (실제 서버에서 확인한 경우)
        server.expect(requestTo(URL)).andRespond(request -> new MockClientHttpResponse(new InputStream() {
            @Override
            public int read() throws SocketTimeoutException {
                throw new SocketTimeoutException("Read timed out");
            }
        }, HttpStatus.OK));
        server.expect(requestTo(URL)).andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        JevResult result = jevClient.askYesNo(IMAGE, "jpg", QUESTIONS);

        // 시간 초과는 응답이 없어서 상태 코드 대신 이유가 남음
        List<JevCallAttempt> attempts = result.attempts();
        assertThat(attempts).hasSize(3);
        assertThat(attempts.get(0).statusCode()).isNull();
        assertThat(attempts.get(0).error()).contains("Connect timed out");
        assertThat(attempts.get(1).statusCode()).isNull();
        assertThat(attempts.get(1).error()).contains("Read timed out");
        assertThat(attempts.get(2).statusCode()).isEqualTo(200);
        server.verify();
    }

    @Test
    void 재시도를_다_해도_실패하면_예외에_시도_기록을_담는다() {
        // 첫 시도 + 재시도 2번 = 3번. 한도 초과(429)와 과부하(529)는 재시도 대상임
        server.expect(requestTo(URL)).andRespond(withTooManyRequests());
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatusCode.valueOf(529)));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatusCode.valueOf(529)));

        JevCallException e = assertThrows(JevCallException.class,
                () -> jevClient.askYesNo(IMAGE, "jpg", QUESTIONS));

        assertThat(e.getAttempts()).extracting(JevCallAttempt::statusCode).containsExactly(429, 529, 529);
        server.verify();
    }

    @Test
    void 잘못된_요청은_재시도하지_않는다() {
        server.expect(requestTo(URL)).andRespond(withBadRequest().body("{\"error\": \"invalid question\"}"));

        JevCallException e = assertThrows(JevCallException.class,
                () -> jevClient.askYesNo(IMAGE, "jpg", QUESTIONS));

        assertThat(e.getAttempts()).hasSize(1);
        assertThat(e.getAttempts().get(0).statusCode()).isEqualTo(400);
        assertThat(e.getAttempts().get(0).responseBody()).contains("invalid question");
        server.verify();
    }
}
```
- Spring 없이 `new JevClient(...)`로 만듦 (가이드 02 `FrameSelectorTest`와 같은 방식). DB도 필요 없어서 빠름
- 재시도 대기 시간(`baseDelayMs`)을 0으로 넣음. 진짜 값(500)이면 테스트 하나에 1.5초씩 기다림
- `jsonPath("$.assets['image-photo'].format")`: 이름에 `-`가 있으면 `.image-photo`로 못 쓰고 `['image-photo']`로 씀
- `new MockClientHttpResponse(new InputStream() {...}, HttpStatus.OK)`: 본문을 읽으려고 하면 시간 초과를 던지는 가짜 응답. 실제 서버에서 난 문제를 재현함 (1-5)
- `assertThrows(JevCallException.class, () -> ...)`: 이 코드가 이 예외를 던져야 함. 던진 예외를 돌려줘서 안에 담긴 시도 기록을 확인할 수 있음
- `extracting(JevCallAttempt::statusCode).containsExactly(429, 529, 529)`: 시도 기록에서 상태 코드만 뽑아 순서까지 비교함

### 3-10. 테스트: `analysis/controller/JudgmentControllerTest.java` (전체 바꿈)
```java
package com.mujinjigi.domain.analysis.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.mujinjigi.domain.analysis.jev.FrameQuestions;
import com.mujinjigi.domain.analysis.jev.JevCallAttempt;
import com.mujinjigi.domain.analysis.jev.JevCallException;
import com.mujinjigi.domain.analysis.jev.JevClient;
import com.mujinjigi.domain.analysis.jev.JevResponse;
import com.mujinjigi.domain.analysis.jev.JevResult;
import com.mujinjigi.domain.analysis.repository.JevCallLogRepository;
import com.mujinjigi.domain.video.TestVideos;

/**
 * 1단계 · 03 (Jev 프레임 판단)
 * 1단계 · 04 (호출 기록, Jev 실패 시 502)
 * 프레임 판단 API 테스트. 진짜 Jev 대신 가짜(JevClient mock)가 정해 둔 답을 돌려줌 (비용·네트워크 없이 실행)
 */
@SpringBootTest(properties = {
        "app.storage.video-dir=build/test-uploads",
        "app.storage.frame-dir=build/test-frames"})
@AutoConfigureMockMvc
@Transactional
class JudgmentControllerTest {

    @Autowired
    MockMvc mockMvc;

    // 시도 기록이 저장됐는지 셀 때 씀
    @Autowired
    JevCallLogRepository jevCallLogRepository;

    // 스프링이 진짜 JevClient 대신 이 가짜를 주입함
    @MockitoBean
    JevClient jevClient;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // 어떤 이미지를 보내도 같은 답을 돌려주게 정해 둠
        JevResponse fakeResponse = new JevResponse(
                "jev-1.14.0",
                Map.of("person", new JevResponse.Answer("noul", 0.9),
                        "putting_in_bag", new JevResponse.Answer("noul", 0.1)),
                new JevResponse.Usage(2803, 0));
        // 한 번에 성공한 것처럼 시도 기록 1개를 같이 돌려줌
        JevResult fakeResult = new JevResult(fakeResponse, "{\"model\": \"jev-1.14.0\"}",
                List.of(new JevCallAttempt(1, LocalDateTime.now(), 200, "{\"model\": \"jev-1.14.0\"}", null, 300)));
        when(jevClient.askYesNo(any(byte[].class), eq("jpg"), eq(FrameQuestions.QUESTIONS)))
                .thenReturn(fakeResult);
    }

    @Test
    void 프레임을_판단하면_결과를_저장하고_돌려준다() throws Exception {
        long videoId = uploadAndExtract();

        // 테스트 영상은 프레임 5장(1~5초) → Jev 5번 호출
        mockMvc.perform(post("/api/videos/{id}/judgments", videoId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].timeSec").value(1.0))
                .andExpect(jsonPath("$[0].questionSetVersion").value("v2"))
                .andExpect(jsonPath("$[0].model").value("jev-1.14.0"))
                .andExpect(jsonPath("$[0].inputTokens").value(2803))
                .andExpect(jsonPath("$[0].answers.person").value(0.9));
        verify(jevClient, times(5)).askYesNo(any(byte[].class), eq("jpg"), eq(FrameQuestions.QUESTIONS));

        mockMvc.perform(get("/api/videos/{id}/judgments", videoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
        // 프레임마다 시도 기록이 1줄씩 남음
        assertThat(jevCallLogRepository.countByFrameVideoId(videoId)).isEqualTo(5);
    }

    @Test
    void 다시_판단하면_이미_판단한_프레임은_건너뛴다() throws Exception {
        long videoId = uploadAndExtract();
        mockMvc.perform(post("/api/videos/{id}/judgments", videoId)).andExpect(status().isCreated());

        mockMvc.perform(post("/api/videos/{id}/judgments", videoId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(5));
        // 두 번 불렀지만 Jev 호출은 처음의 5번뿐임
        verify(jevClient, times(5)).askYesNo(any(byte[].class), eq("jpg"), eq(FrameQuestions.QUESTIONS));
    }

    @Test
    void Jev가_재시도까지_실패하면_502를_돌려주고_시도_기록을_남긴다() throws Exception {
        List<JevCallAttempt> failed = List.of(
                new JevCallAttempt(1, LocalDateTime.now(), 529, "overloaded", null, 100),
                new JevCallAttempt(2, LocalDateTime.now(), 529, "overloaded", null, 100),
                new JevCallAttempt(3, LocalDateTime.now(), 529, "overloaded", null, 100));
        when(jevClient.askYesNo(any(byte[].class), eq("jpg"), eq(FrameQuestions.QUESTIONS)))
                .thenThrow(new JevCallException("Jev 호출 실패: 상태 코드 529, 3번 시도", "{}", failed, null));
        long videoId = uploadAndExtract();

        mockMvc.perform(post("/api/videos/{id}/judgments", videoId))
                .andExpect(status().isBadGateway());
        assertThat(jevCallLogRepository.countByFrameVideoId(videoId)).isEqualTo(3);
    }

    @Test
    void 프레임을_추출하지_않은_영상은_409를_돌려준다() throws Exception {
        long videoId = upload();

        mockMvc.perform(post("/api/videos/{id}/judgments", videoId))
                .andExpect(status().isConflict());
    }

    @Test
    void 없는_영상은_404를_돌려준다() throws Exception {
        mockMvc.perform(post("/api/videos/{id}/judgments", 999_999))
                .andExpect(status().isNotFound());
    }

    // 테스트 영상을 올리고 프레임까지 추출한 뒤 영상 id를 돌려줌
    private long uploadAndExtract() throws Exception {
        long videoId = upload();
        mockMvc.perform(post("/api/videos/{id}/frames", videoId)).andExpect(status().isCreated());
        return videoId;
    }

    private long upload() throws Exception {
        Path video = TestVideos.blackThenWhite(tempDir);
        MockMultipartFile file = new MockMultipartFile("file", "test.mp4", "video/mp4", Files.readAllBytes(video));

        String body = mockMvc.perform(multipart("/api/videos").file(file))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.id");
        return id.longValue();
    }
}
```
바뀐 곳:
- 가짜 `JevClient`가 `JevResponse` 대신 `JevResult`(시도 기록 1개 포함)를 돌려줌
- 첫 테스트 끝에 시도 기록이 프레임 수(5)만큼 남았는지 확인함
- 새 테스트 `Jev가_재시도까지_실패하면_502를_돌려주고_시도_기록을_남긴다`: `thenThrow`로 가짜가 예외를 던지게 함. `setUp`의 `thenReturn`보다 나중에 정해서 이 테스트에서만 덮어씀

---

## 4. 확인

### 4-1. 테스트
Docker PostgreSQL이 켜져 있어야 함 (`docs/guide/README.md` 참고).
```powershell
cd backend
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-21.0.12"
.\gradlew.bat test
```
- `BUILD SUCCESSFUL`이 나오면 통과임 (전체 22개)
- `JevClientTest`는 DB 없이도 돌지만, 다른 테스트 때문에 Docker는 켜 둠

### 4-2. 실제 서버로 확인 (진짜 Jev 호출, 약 0.004달러)
**① 정상 판단**: 서버 실행 (backend 폴더에서)
```powershell
.\gradlew.bat bootRun
```
다른 터미널에서 (저장소 루트에서). 업로드 응답의 `id`로 바꿔서 차례로 부름:
```powershell
curl.exe -i -F "file=@docs/test-data/videos/P1.mp4" http://localhost:8080/api/videos
curl.exe -i -X POST http://localhost:8080/api/videos/3/frames
curl.exe -i -X POST http://localhost:8080/api/videos/3/judgments
```
기록 확인 (`<DB_USERNAME>`, `<DB_NAME>`은 `.env`의 값으로 바꿈):
```powershell
docker exec -it mujinjigi-postgres psql -U <DB_USERNAME> -d <DB_NAME> -c "select frame_id, attempt, status_code, elapsed_ms, left(request_body, 80) from jev_call_logs order by id desc limit 5;"
```
- 16줄이 생기고 모두 `attempt=1`, `status_code=200`이면 됨. `request_body`에 `(생략: 204219 bytes)`처럼 이미지 대신 크기가 보여야 함

**② 일부러 시간 초과 만들기**: 서버를 끄고(Ctrl+C) 응답 시간 제한을 1ms로 줄여 다시 띄움
```powershell
.\gradlew.bat bootRun --args="--jev.read-timeout-ms=1"
```
새 영상을 올리고 프레임을 뽑은 뒤 판단을 부름 (영상 id는 새 값으로):
```powershell
curl.exe -i -F "file=@docs/test-data/videos/P1.mp4" http://localhost:8080/api/videos
curl.exe -i -X POST http://localhost:8080/api/videos/4/frames
curl.exe -i -X POST http://localhost:8080/api/videos/4/judgments
```
- `HTTP/1.1 502`가 오면 됨. 2초쯤 걸림 (0.5초 + 1초 대기)
```powershell
docker exec -it mujinjigi-postgres psql -U <DB_USERNAME> -d <DB_NAME> -c "select attempt, status_code, elapsed_ms, called_at, right(error, 30) from jev_call_logs order by id desc limit 3;"
```
- 3줄이 생기고 `status_code`는 비어 있고 `error`는 `Read timed out`이어야 함
- `called_at` 간격을 보면 재시도 사이에 약 0.4~0.5초, 0.75~1초를 기다린 것을 확인할 수 있음 (지터 때문에 매번 조금 다름)

**③ 이어서 하기**: 서버를 끄고 원래 설정으로 다시 띄운 뒤(`.\gradlew.bat bootRun`), ②의 영상에 판단을 다시 부름
```powershell
curl.exe -i -X POST http://localhost:8080/api/videos/4/judgments
```
- `201`과 판단 16개가 오면 됨. ②에서 실패한 0초 프레임부터 다시 판단한 것임
- Claude Code가 확인한 값 (2026-10-30): `docs/portfolio/assets/2026-10-30_Jev_시간초과_재시도.txt`

---

## 5. 알려진 한계 (면접에서 물어볼 만한 부분)
1. **재시도하는 동안 요청 스레드가 기다림**: 최악이면 프레임 하나에 30초 × 3번 + 대기 1.5초. 판단 요청이 동기라서 그동안 응답이 오지 않음 → 2단계에서 작업(Job)으로 바꿀 때 해결함
2. **`Retry-After` 헤더를 보지 않음**: 서버가 "몇 초 뒤에 다시 보내라"고 알려 줘도 정해진 간격으로 기다림. Jev 문서에는 이 헤더 언급이 없어서 넣지 않음. 공식 SDK는 이 헤더가 있으면 따름
3. **동시에 여러 요청이 실패하면 각자 재시도함**: 지터로 몰림은 줄지만, 전체 재시도 수를 제한하지는 않음. 병렬 호출을 도입할 때 동시 개수 제한과 같이 다룸
4. **기록이 계속 쌓임**: 시도 한 줄 약 1KB, 하루 14,400장 기준 약 15MB/일. 보관 기간(예: 30일)을 정해 지우는 작업이 필요함
5. **응답을 받았지만 JSON이 깨진 경우**: `200`인데 본문을 `JevResponse`로 못 읽으면 예외가 나고 그 시도의 기록은 저장되지 않음. 실제로 본 적은 없어서 그대로 둠
6. **요청이 Jev에 도착한 뒤 응답만 늦은 경우에도 다시 보냄**: Jev는 판단만 하고 상태를 바꾸지 않아서 두 번 보내도 결과는 같음. 다만 입력 토큰 비용은 두 번 나감

---

## 6. 다 했으면
"가이드 04 다 했어"라고 알려 주세요. 이렇게 진행합니다.
1. 작성한 코드 리뷰, 0단계 실제 호출 테스트 4개를 새 반환 타입에 맞춰 고침
2. 테스트 실행과 커버리지 확인
3. 통과하면 학습 노트 작성과 기능 단위 커밋

## 스스로 답해볼 질문
1. 어떤 실패는 재시도하고 어떤 실패는 하지 않는가. 400을 재시도하면 무엇이 문제인가 (면접 질문)
2. 지수 백오프에 지터를 더하는 이유는 무엇인가 (면접 질문)
3. 재시도가 안전하려면 호출하는 쪽에 어떤 성질이 필요한가. 이 프로젝트는 그것을 어떻게 갖췄는가 (멱등성)
4. `@MockitoBean`으로 `JevClient`를 가짜로 만든 테스트와 `MockRestServiceServer`로 HTTP 서버만 가짜로 만든 테스트는 각각 무엇을 확인하고, 무엇을 놓치는가
5. 가짜 서버 테스트가 통과했는데도 실제 서버에서 시간 초과가 재시도되지 않은 이유는 무엇이고, 어떻게 찾았는가
