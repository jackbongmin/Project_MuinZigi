package com.mujinjigi.domain.analysis.entity;

import java.time.LocalDateTime;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.mujinjigi.domain.video.entity.Video;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 1단계 · 05 (측정 로그)
 * 처리 단계를 한 번 실행할 때마다 걸린 시간·프레임 수·Jev 호출 수·토큰을 DB(measurement_logs 테이블)에 한 줄 남기는 엔티티
 * 예: P1 판단 한 번 → JUDGMENT, 성공, 6.3초, 16장, Jev 16번, 44,848토큰
 * 단계를 시작할 때 만들고, 진행하면서 숫자를 더하다가, 끝날 때 finish()로 걸린 시간과 성공 여부를 채움
 */
@Entity
@Table(name = "measurement_logs")
public class MeasurementLog {

    @Id
    @GeneratedValue(strategy =  GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_id")
    private Video video;

    // 어떤 단계의 측정인지. 순서 번호(0, 1)가 아니라 이름(JUDGMENT)으로 저장함
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MeasurementStep step;

    // 끝까지 성공했는지. Jev가 재시도까지 실패하면 false로 남음
    @Column(nullable = false)
    private boolean success;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column(nullable = false)
    private long elapsedMs;

    // 1초에 1장씩 뽑은 전체 프레임 수 (= 영상 길이(초)). 프레임 추출에서만 채우고 판단에서는 비워 둠
    private Integer extractedFrameCount;

    // 이 단계에서 저장한 프레임 수. 추출: 고른 프레임 수 / 판단: 이번에 판단한 프레임 수
    @Column(nullable = false)
    private int frameCount;

    // Jev에 실제로 보낸 요청 수 (재시도 포함)
    @Column(nullable = false)
    private int jevCallCount;

    // 그중 재시도한 수
    @Column(nullable = false)
    private int retryCount;

    // 성공한 판단의 입력 토큰 합계 (과금 대상)
    @Column(nullable = false)
    private long inputTokens;

    // 이 측정에 쓴 설정값 (scene 기준값, 질문 세트 버전 등). 단계마다 다르고 2단계에서 늘어나서 jsonb 한 칸에 담음
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> settings;

    protected MeasurementLog() {
    }

    // 단계를 시작할 때 만듦. 시작 시각은 지금으로 둠
    public MeasurementLog(Video video, MeasurementStep step, Map<String, Object> settings) {
        this.video = video;
        this.step = step;
        this.settings = settings;
        this.startedAt = LocalDateTime.now();
    }

    // 프레임 추출 결과를 기록함
    public void recordFrames(int extractedFrameCount, int selectedFrameCount) {
        this.extractedFrameCount = extractedFrameCount;
        this.frameCount = selectedFrameCount;
    }

    // 프레임 하나를 판단하며 Jev에 보낸 시도 수를 더함 (성공·실패 모두). 2번 이상이면 그만큼 재시도한 것임
    public void addJevCalls(int attempts) {
        this.jevCallCount += attempts;
        this.retryCount += attempts - 1;
    }

    // 판단에 성공한 프레임 하나를 더함
    public void addJudgedFrame(int inputTokens) {
        this.frameCount++;
        this.inputTokens += inputTokens;
    }

    // 단계가 끝났을 때 부름
    public void finish(boolean success, long elapsedMs) {
        this.success = success;
        this.elapsedMs = elapsedMs;
    }

    public Long getId() {
        return id;
    }

    public MeasurementStep getStep() {
        return step;
    }

    public boolean isSuccess() {
        return success;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public Integer getExtractedFrameCount() {
        return extractedFrameCount;
    }

    public int getFrameCount() {
        return frameCount;
    }

    public int getJevCallCount() {
        return jevCallCount;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public long getInputTokens() {
        return inputTokens;
    }

    public Map<String, Object> getSettings() {
        return settings;
    }
}
