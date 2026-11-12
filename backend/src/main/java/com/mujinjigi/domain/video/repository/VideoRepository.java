package com.mujinjigi.domain.video.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mujinjigi.domain.video.entity.Video;

/**
 * 1단계 · 01 (영상 업로드 API)
 * videos 테이블에서 영상 정보를 저장·조회하는 저장소. save, findById 같은 기본 메서드는 JpaRepository가 만들어 줌
 * 예: findById(69) → 영상 69번 정보 (없으면 빈 Optional)
 */
public interface VideoRepository extends JpaRepository<Video, Long> {
}
