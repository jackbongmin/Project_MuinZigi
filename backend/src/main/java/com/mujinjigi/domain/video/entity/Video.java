package com.mujinjigi.domain.video.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


/**
 * 업로드한 녹화본 한 개. 파일 자체는 디스크에 두고, DB에는 파일을 찾을 정보만 저장함
 */

@Entity
@Table(name = "videos")
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 사용자가 올린 파일 이름(화면에 보여줄 용도)
    @Column(nullable = false)
    private String originalName;

    // 디스크에 저장한 파일 이름. 이름이 겹치지 않도록 UUID로 만듦
    @Column(nullable = false, unique = true)
    private String storedName;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    // JPA가 DB에서 읽어 객체를 만들 때 쓰는 기본 생성자. 다른 코드는 쓰지 않게 protected
    protected Video() {

    }

    public Video(String originalName, String storedName, long sizeBytes) {
        this.originalName = originalName;
        this.storedName = storedName;
        this.sizeBytes = sizeBytes;
        this.uploadedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getStoredName() {
        return storedName;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

}
