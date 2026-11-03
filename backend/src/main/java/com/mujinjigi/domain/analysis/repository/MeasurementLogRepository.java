package com.mujinjigi.domain.analysis.repository;


import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mujinjigi.domain.analysis.entity.MeasurementLog;

/**
 * 1단계 · 05 (측정 로그)
 * MeasurementLog 저장·조회
 */

public interface MeasurementLogRepository extends JpaRepository<MeasurementLog, Long>{

    // 영상 하나의 측정 로그를 남긴 순서대로 (video.id기준). 메서드 이름 규칙으로 쿼리를 만듦
    List<MeasurementLog> findByVideoIdOrderByIdAsc(Long videoId);
}
