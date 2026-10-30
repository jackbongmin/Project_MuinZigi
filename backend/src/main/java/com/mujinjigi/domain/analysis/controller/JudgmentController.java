package com.mujinjigi.domain.analysis.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mujinjigi.domain.analysis.dto.FrameJudgmentResponse;
import com.mujinjigi.domain.analysis.service.JudgmentService;


/**
 * 1단계 · 03 (Jev 프레임 판단)
 * 프레임 Jev 판단 API
 */
@RestController
@RequestMapping("/api/videos/{videoId}/judgments")
public class JudgmentController {

    private final JudgmentService judgmentService;

    public JudgmentController(JudgmentService judgmentService) {
        this.judgmentService = judgmentService;
    }

    // POST → 201 + 판단 목록. 이미 판단한 프레임은 건너뜀. 없는 영상 404, 프레임을 아직 안 뽑았으면 409
    @PostMapping
    public ResponseEntity<List<FrameJudgmentResponse>> judge(@PathVariable Long videoId) {
        List<FrameJudgmentResponse> judgments = judgmentService.judge(videoId);
        return ResponseEntity.created(URI.create("/api/videos/" + videoId + "/judgments")).body(judgments);
    }

    // GET → 200 + 시각 순 판단 목록
    @GetMapping
    public List<FrameJudgmentResponse> findAll(@PathVariable Long videoId) {
        return judgmentService.findAll(videoId);
    }
}
