package com.mujinjigi.domain.analysis.entity;

import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 * 1단계 · 03 (Jev 프레임 판단)
 * 프레임 한 장에 대한 Jev 판단 결과. 질문마다 "예"일 확률을 저장함
 */
@Entity
@Table(name = "frame_judgments")
public class FrameJudgment {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "frame_id")
    private VideoFrame frame;

    // 어떤 질문 세트로 물었는지 (v2). 질문을 바꾼 뒤 결과를 비교할 때 씀
    @Column(nullable = false)
    private String questionSetVersion;

    // Jev가 실제로 답한 모델 이름 (응답의 model 값)
    @Column(nullable = false)
    private String model;

    // 이 요청의 입력 토큰 수 (과금 대상). 비용 계산에 씀
    @Column(nullable = false)
    private int inputTokens;

    // 질문 이름 → "예"일 확률. 질문 세트가 바뀌어도 테이블을 고치지 않도록 JSON(jsonb) 한 칸에 담음
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Double> answers;

    protected FrameJudgment() {
    }

    public FrameJudgment(VideoFrame frame, String questionSetVersion, String model, int inputTokens,
                         Map<String, Double> answers) {
        this.frame = frame;
        this.questionSetVersion = questionSetVersion;
        this.model = model;
        this.inputTokens = inputTokens;
        this.answers = answers;
    }

    public Long getId() {
        return id;
    }

    public VideoFrame getFrame() {
        return frame;
    }

    public String getQuestionSetVersion() {
        return questionSetVersion;
    }

    public String getModel() {
        return model;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public Map<String, Double> getAnswers() {
        return answers;
    }


}
