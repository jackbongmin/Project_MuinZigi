package com.mujinjigi.domain.video.entity;

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
 * 1단계 · 02 (프레임 추출과 고르기)
 * 영상에서 뽑아 Jev에 보낼 프레임 한 장. 이미지 파일은 디스크에 두고 DB에는 위치와 시각만 저장함
 */

@Entity
@Table(name = "video_frames")
public class VideoFrame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 여러 프레임이 한 영상에 속함. 프레임을 읽을 때 영상까지 바로 읽지 않도록 LAZY로 둠
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_id")
    private Video video;

    // 영상 안에서의 시각(초). 나중에 결제 시각과 맞춰볼 때 씀
    @Column(nullable = false)
    private double timeSec;

    // 직전 프레임과 얼마나 다른지(0~1)
    @Column(nullable = false)
    private double sceneScore;

    // 프레임 폴더 안의 파일 이름 (frame_0001.jpg)
    @Column(nullable = false)
    private String fileName;

    protected VideoFrame() {
    }

    public VideoFrame(Video video, double timeSec, double sceneScore, String fileName) {
        this.video = video;
        this.timeSec = timeSec;
        this.sceneScore = sceneScore;
        this.fileName = fileName;
    }

    public Long getId() {
        return id;
    }

    public Video getVideo() {
        return video;
    }

    public double getTimeSec() {
        return timeSec;
    }

    public double getSceneScore() {
        return sceneScore;
    }

    public String getFileName() {
        return fileName;
    }
}
