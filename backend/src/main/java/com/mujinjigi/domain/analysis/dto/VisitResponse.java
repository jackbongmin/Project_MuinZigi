package com.mujinjigi.domain.analysis.dto;


import com.mujinjigi.domain.analysis.entity.Visit;

/**
 * 2단계 · 08 (방문 나누기)
 * 방문 API가 돌려주는 JSON 한 칸의 모양
 * 예: {"id": 4, "startSec": 11.0, "endSec": 19.0, "frameCount": 9}
 */
public record VisitResponse(Long id, double startSec, double endSec, int frameCount) {

    public static VisitResponse from(Visit visit) {
        return new VisitResponse(visit.getId(), visit.getStartSec(), visit.getEndSec(), visit.getFrameCount());
    }
}
