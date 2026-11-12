package com.mujinjigi.domain.analysis.entity;


import com.mujinjigi.domain.video.entity.Video;

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
 * 2단계 · 08 (방문 나누기)
 * 손님 한 명이 매장에 머문 구간을 DB(visits 테이블)에 저장하는 엔티티
 * 예: 영상 69번의 11초~19초에 손님이 있었음 → video 69, startSec 11, endSec 19, frameCount 9
 * 다음 단계의 진열대 비교·결제 대조·의심 장면이 이 구간을 기준으로 함 (결정 006)
 */
@Entity
@Table(name = "visits")
public class Visit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_id")
    private Video video;

    // 사람이 처음 보인 시각(초)
    @Column(nullable = false)
    private double startSec;

    // 사람이 마지막으로 보인 시각(초)
    @Column(nullable = false)
    private double endSec;

    // 방문 안에서 사람이 보인 프레임 수
    @Column(nullable = false)
    private int frameCount;

    protected Visit() {
    }

    public Visit(Video video, double startSec, double endSec, int frameCount) {
        this.video = video;
        this.startSec = startSec;
        this.endSec = endSec;
        this.frameCount = frameCount;
    }

    public Long getId() {
        return id;
    }

    public Video getVideo() {
        return video;
    }

    public double getStartSec() {
        return startSec;
    }

    public double getEndSec() {
        return endSec;
    }

    public int getFrameCount() {
        return frameCount;
    }
}
