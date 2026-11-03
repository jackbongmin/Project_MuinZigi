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
