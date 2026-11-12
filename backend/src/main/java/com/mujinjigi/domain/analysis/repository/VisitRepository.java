package com.mujinjigi.domain.analysis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mujinjigi.domain.analysis.entity.Visit;

/**
 * 2단계 · 08 (방문 나누기)
 * visits 테이블에서 방문을 저장·조회·삭제하는 저장소. 메서드 이름만 쓰면 Spring Data가 쿼리를 만들어 줌
 */
public interface VisitRepository extends JpaRepository<Visit, Long> {

    List<Visit> findByVideoIdOrderByStartSecAsc(Long videoId);

    // 다시 나눌 때 이전 방문을 지움. 트랜잭션 안에서 불러야 함
    void deleteByVideoId(Long videoId);
}