package com.mujinjigi.domain.analysis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.mujinjigi.domain.analysis.entity.FrameJudgment;

/**
 * 1단계 · 03 (Jev 프레임 판단)
 * FrameJudgment 저장·조회
 */
public interface FrameJudgmentRepository extends JpaRepository<FrameJudgment, Long> {

    // 판단 결과와 프레임을 한 번의 쿼리로 같이 읽음 (fetch join). 응답에 프레임 시각이 필요해서
    // 이렇게 안 하면 결과 N개를 읽은 뒤 프레임을 N번 더 조회함 (N+1 문제)
    @Query("""
            select j from FrameJudgment j
            join fetch j.frame f
            where f.video.id = :videoId and j.questionSetVersion = :version
            order by f.timeSec
            """)
    List<FrameJudgment> findAllWithFrame(Long videoId, String version);
}