package com.mujinjigi.domain.video.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mujinjigi.domain.video.entity.VideoFrame;

/**
 * 1단계 · 02 (프레임 추출과 고르기)
 * video_frames 테이블에서 프레임을 저장·조회하는 저장소. 메서드 이름만 쓰면 Spring Data가 쿼리를 만들어 줌
 * 예: findByVideoIdOrderByTimeSecAsc(69) → 영상 69번의 프레임을 시각 순으로
 */
public interface VideoFrameRepository extends JpaRepository<VideoFrame, Long> {

    List<VideoFrame> findByVideoIdOrderByTimeSecAsc(Long videoId);

    boolean existsByVideoId(Long videoId);
}