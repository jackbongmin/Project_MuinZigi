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