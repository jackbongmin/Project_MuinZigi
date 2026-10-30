package com.mujinjigi.domain.video.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mujinjigi.domain.video.TestVideos;

/**
 * FFmpeg를 실제로 실행해 프레임과 장면 변화 점수를 뽑는지 확인함 (PATH에 ffmpeg가 있어야 함)
 */
class FrameExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void 일초에_한장씩_뽑고_장면이_바뀐_시각의_점수가_높다() throws Exception {
        Path video = TestVideos.blackThenWhite(tempDir);
        FrameExtractor extractor = new FrameExtractor("ffmpeg");

        List<ExtractedFrame> frames = extractor.extract(video, tempDir.resolve("frames"));

        // 6초 영상 → 0~5초 6장
        assertThat(frames).extracting(ExtractedFrame::timeSec)
                .containsExactly(0.0, 1.0, 2.0, 3.0, 4.0, 5.0);
        // 검은 화면 → 흰 화면으로 바뀌는 3초만 점수가 높음
        assertThat(frames.get(3).sceneScore()).isGreaterThan(0.5);
        assertThat(frames.get(2).sceneScore()).isLessThan(0.005);
        // 프레임 파일이 실제로 생김
        assertThat(tempDir.resolve("frames").resolve(frames.get(0).fileName())).exists();
        assertThat(Files.list(tempDir.resolve("frames")).filter(p -> p.toString().endsWith(".jpg"))).hasSize(6);
    }
}
