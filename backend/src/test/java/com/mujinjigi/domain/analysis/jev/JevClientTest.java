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
