package com.mujinjigi.domain.video.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mujinjigi.domain.video.dto.VideoFrameResponse;
import com.mujinjigi.domain.video.service.FrameService;

/**
 * 1단계 · 02 (프레임 추출과 고르기)
 * 영상 프레임 추출·조회 API
 */
@RestController
@RequestMapping("/api/videos/{videoId}/frames")
public class VideoFrameController {

    private final FrameService frameService;

    public VideoFrameController(FrameService frameService) {
        this.frameService = frameService;
    }

    // POST /api/videos/{videoId}/frames → 201 + 고른 프레임 목록. 없는 영상 404, 이미 추출했으면 409
    @PostMapping
    public ResponseEntity<List<VideoFrameResponse>> extract(@PathVariable Long videoId) {
        List<VideoFrameResponse> frames = frameService.extract(videoId);
        return ResponseEntity.created(URI.create("/api/videos/" + videoId + "/frames")).body(frames);
    }

    // GET /api/videos/{videoId}/frames → 200 + 시각 순 프레임 목록
    @GetMapping
    public List<VideoFrameResponse> findAll(@PathVariable Long videoId) {
        return frameService.findAll(videoId);
    }
}