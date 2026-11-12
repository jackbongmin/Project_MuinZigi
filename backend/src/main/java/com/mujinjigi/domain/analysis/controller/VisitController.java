package com.mujinjigi.domain.analysis.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mujinjigi.domain.analysis.dto.VisitResponse;
import com.mujinjigi.domain.analysis.service.VisitService;

/**
 * 2단계 · 08 (방문 나누기)
 * 방문 API 요청을 받는 클래스
 * POST /api/videos/{id}/visits → 방문을 잘라 저장하고 목록을 돌려줌 / GET → 저장된 방문 목록
 */
@RestController
@RequestMapping("/api/videos/{videoId}/visits")
public class VisitController {

    private final VisitService visitService;

    public VisitController(VisitService visitService) {
        this.visitService = visitService;
    }

    // POST → 201 + 방문 목록. 다시 부르면 이전 방문을 지우고 새로 나눔. 없는 영상 404, 판단 전이면 409
    @PostMapping
    public ResponseEntity<List<VisitResponse>> split(@PathVariable Long videoId) {
        List<VisitResponse> visits = visitService.split(videoId);
        return ResponseEntity.created(URI.create("/api/videos/" + videoId + "/visits")).body(visits);
    }

    // GET → 200 + 시각 순 방문 목록
    @GetMapping
    public List<VisitResponse> findAll(@PathVariable Long videoId) {
        return visitService.findAll(videoId);
    }
}