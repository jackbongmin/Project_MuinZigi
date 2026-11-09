package com.mujinjigi.domain.video.service;

import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.mujinjigi.domain.video.dto.VideoResponse;
import com.mujinjigi.domain.video.entity.Video;
import com.mujinjigi.domain.video.repository.VideoRepository;


/**
 * 1단계 · 01 (영상 업로드 API)
 * 영상 업로드와 조회
 */
@Service
public class VideoService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("mp4", "mov", "avi", "mkv");

    private final VideoRepository videoRepository;
    private final VideoFileStorage videoFileStorage;

    public VideoService(VideoRepository videoRepository, VideoFileStorage videoFileStorage) {
        this.videoRepository = videoRepository;
        this.videoFileStorage = videoFileStorage;
    }

    @Transactional
    public VideoResponse upload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "빈 파일은 올릴 수 없음");
        }

        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "영상 파일(mp4, mov, avi, mkv)만 올릴 수 있음");
        }

        String storedName = videoFileStorage.save(file, extension);
        Video video = videoRepository.save(new Video(file.getOriginalFilename(), storedName, file.getSize()));
        return VideoResponse.from(video);
    }

    @Transactional(readOnly = true)
    public VideoResponse find(Long id) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상이 없음: " + id));
        return VideoResponse.from(video);
    }

    // "P1.MP4" → "mp4". 점이 없으면 빈 문자열
    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

}
