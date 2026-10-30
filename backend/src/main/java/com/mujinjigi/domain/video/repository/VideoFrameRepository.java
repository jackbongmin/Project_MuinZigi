package com.mujinjigi.domain.video.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mujinjigi.domain.video.entity.VideoFrame;

/**
 * VideoFrame 저장·조회. 메서드 이름 규칙으로 쿼리를 만들어 줌 (video.id 기준으로 찾고 시각 순으로 정렬)
 */
public interface VideoFrameRepository extends JpaRepository<VideoFrame, Long> {

    List<VideoFrame> findByVideoIdOrderByTimeSecAsc(Long videoId);

    boolean existsByVideoId(Long videoId);
}