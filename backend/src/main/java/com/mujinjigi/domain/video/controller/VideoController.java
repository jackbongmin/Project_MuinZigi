package com.mujinjigi.domain.video.controller;

import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.mujinjigi.domain.video.dto.VideoResponse;
import com.mujinjigi.domain.video.service.VideoService;

/**
 * 영상 업로드 조회 API
 */
@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    // POST /api/videos (form-data, 필드 이름 file) -> 201 Created + 새 영상 주소(Location 헤더)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VideoResponse> upload(@RequestParam("file") MultipartFile file) {
        VideoResponse response = videoService.upload(file);
        return ResponseEntity.created(URI.create("/api/videos/" + response.id())).body(response);
    }

    // GET /api/videos/{id} → 200 OK, 없으면 404
    @GetMapping("/{id}")
    public VideoResponse find(@PathVariable Long id) {
        return videoService.find(id);
    }
}
