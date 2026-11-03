package com.mujinjigi.domain.analysis.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mujinjigi.domain.analysis.entity.JevCallLog;

/**
 * 1단계 · 04 (Jev 재시도와 원문 로그)
 * JevCallLog 저장·조회
 */
public interface JevCallLogRepository extends JpaRepository<JevCallLog, Long> {

    // 영상 하나에서 남긴 시도 기록 수 (frame.video.id 기준). 메서드 이름 규칙으로 쿼리를 만듦
    long countByFrameVideoId(Long videoId);
}