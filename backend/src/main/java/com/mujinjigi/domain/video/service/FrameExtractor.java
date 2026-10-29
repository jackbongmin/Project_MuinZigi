package com.mujinjigi.domain.video.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * FFmpeg로 영상에서 1초에 1장씩 프레임을 뽑고, 프레임마다 장면 변화 점수를 함께 구함
 */
@Component
public class FrameExtractor {

    private static final String SCORE_FILE = "scores.txt";

    // fps=1: 1초에 1장 / scale: 가로가 1280보다 크면 1280으로 줄임 (Jev 제한 1,568px, 토큰 절약)
    // select='gte(scene,0)': 모든 프레임을 통과시키면서 scene 점수를 계산하게 함
    // metadata=print: 프레임마다 시각과 scene 점수를 scores.txt에 씀
    private static final String FILTER = "fps=1,scale='min(1280,iw)':-2,select='gte(scene,0)',"
            + "metadata=print:key=lavfi.scene_score:file=" + SCORE_FILE;

    private final String ffmpegPath;

    public FrameExtractor(@Value("${app.ffmpeg.path}") String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    public List<ExtractedFrame> extract(Path videoPath, Path outputDir) {
        try {
            Files.createDirectories(outputDir);
            runFfmpeg(videoPath, outputDir);
            return readScores(outputDir.resolve(SCORE_FILE));
        } catch (IOException e) {
            throw new UncheckedIOException("프레임 추출 실패: " + videoPath, e);
        }
    }

    private void runFfmpeg(Path videoPath, Path outputDir) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(
                ffmpegPath, "-hide_banner", "-loglevel", "error", "-y",
                "-i", videoPath.toAbsolutePath().toString(),
                "-vf", FILTER,
                "-fps_mode", "passthrough",
                "-q:v", "2",
                "frame_%04d.jpg");
        // scores.txt와 프레임 파일이 이 폴더에 생기도록 실행 폴더를 바꿈
        builder.directory(outputDir.toFile());
        // 오류 출력도 한 곳에서 읽음. 출력을 읽지 않으면 버퍼가 차서 FFmpeg가 멈출 수 있음
        builder.redirectErrorStream(true);

        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("FFmpeg 실패 (종료 코드 " + exitCode + "): " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("FFmpeg 실행 중 중단됨", e);
        }
    }

    // scores.txt는 프레임마다 두 줄씩 있음
    //  frame:0     pts:0     pts_time:0
    //  lavfi.scene_score=0.000000
    private List<ExtractedFrame> readScores(Path scoreFile) throws IOException {
        List<ExtractedFrame> frames = new ArrayList<>();
        double timeSec = 0;
        for (String line : Files.readAllLines(scoreFile)) {
            if (line.startsWith("frame:")) {
                timeSec = Double.parseDouble(line.substring(line.indexOf("pts_time:") + "pts_time:".length()).trim());
            } else if (line.startsWith("lavfi.scene_score=")) {
                double score = Double.parseDouble(line.substring("lavfi.scene_score=".length()));
                // 파일 번호는 1부터 시작함 (frame_0001.jpg가 첫 프레임)
                String fileName = String.format("frame_%04d.jpg", frames.size() + 1);
                frames.add(new ExtractedFrame(fileName, timeSec, score));
            }
        }
        return frames;
    }


}

