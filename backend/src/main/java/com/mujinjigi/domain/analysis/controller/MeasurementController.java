package com.mujinjigi.domain.analysis.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mujinjigi.domain.analysis.dto.MeasurementLogResponse;
import com.mujinjigi.domain.analysis.service.MeasurementService;

/**
 * 1단계 · 05 (측정 로그)
 * 측정 로그 조회 API
 */
@RestController
@RequestMapping("/api/videos/{videoId}/measurements")
public class MeasurementController {

    private final MeasurementService measurementService;

    public MeasurementController(MeasurementService measurementService) {
        this.measurementService = measurementService;
    }

    // Get -> 200 + 남긴 순서대로 측정 로그 목록. 없는 영상 404
    @GetMapping
    public List<MeasurementLogResponse> findAll(@PathVariable Long videoId) {
        return measurementService.findAll(videoId);
    }


}
