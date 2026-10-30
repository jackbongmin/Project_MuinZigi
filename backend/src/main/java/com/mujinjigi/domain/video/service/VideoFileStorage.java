package com.mujinjigi.domain.video.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드한 영상 파일을 디스크에 저장함. 배포 때 S3로 바꾸면 이 클래스만 바꾸면됨 ㅇㅇ
 */
@Component
public class VideoFileStorage {

    private final Path videoDir;

    public VideoFileStorage(@Value("${app.storage.video-dir}") String videoDir) {
        this.videoDir = Path.of(videoDir).toAbsolutePath().normalize();
    }

    /**
     * @return 디스크에 저장한 파일이름(UUID.확장자)
     */
    public String save(MultipartFile file, String extension) {
        String storedName = UUID.randomUUID() + "." + extension;
        try{
            Files.createDirectories(videoDir);
            file.transferTo(videoDir.resolve(storedName));
        } catch (IOException e) {
            throw new UncheckedIOException("영상 파일 저장 실패 : " + storedName, e);
        }
        return storedName;
    }

    // 저장 이름으로 디스크의 실제 경로를 돌려줌 (프레임 추출할 때 씀)
    public Path pathOf(String storedName) {
        return videoDir.resolve(storedName);
    }
}
