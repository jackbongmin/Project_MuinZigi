package com.mujinjigi.domain.video.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mujinjigi.domain.video.entity.Video;

/**
 * 1단계 · 01 (영상 업로드 API)
 * Video 저장, 조회.
 * save, findById 같은 기본 메서드는 JpaRepository가 만들어줌
 */
public interface VideoRepository extends JpaRepository<Video, Long> {
}
