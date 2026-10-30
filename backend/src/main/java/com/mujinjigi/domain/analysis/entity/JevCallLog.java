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