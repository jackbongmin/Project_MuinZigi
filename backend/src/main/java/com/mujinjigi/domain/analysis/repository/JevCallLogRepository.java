package com.mujinjigi.domain.analysis.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mujinjigi.domain.analysis.entity.JevCallLog;

/**
 * 1단계 · 04 (Jev 재시도와 원문 로그)
 * jev_call_logs 테이블에서 Jev 호출 기록을 저장·조회하는 저장소
 * 예: countByFrameVideoId(69) → 영상 69번 프레임들에 대해 Jev에 요청을 보낸 횟수 (재시도 포함)
 */
public interface JevCallLogRepository extends JpaRepository<JevCallLog, Long> {

    // 영상 하나에서 남긴 시도 기록 수 (frame.video.id 기준). 메서드 이름 규칙으로 쿼리를 만듦
    long countByFrameVideoId(Long videoId);
}