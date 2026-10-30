package com.mujinjigi.domain.video;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 1단계 · 02 (프레임 추출과 고르기)
 * 테스트용 짧은 영상을 FFmpeg로 만듦. 검은 화면 3초 → 흰 화면 3초 (3초에서 장면이 크게 바뀜)
 * 실제 영상 파일을 저장소에 넣지 않으려고 테스트할 때마다 만듦
 */
public final class TestVideos {

    private TestVideos() {
    }

    public static Path blackThenWhite(Path dir) throws IOException, InterruptedException {
        Path video = dir.resolve("black_then_white.mp4");
        Process process = new ProcessBuilder(
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "color=c=black:s=640x360:d=3:r=24",
                "-f", "lavfi", "-i", "color=c=white:s=640x360:d=3:r=24",
                "-filter_complex", "[0][1]concat=n=2:v=1",
                "-c:v", "mpeg4",
                video.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) {
            throw new IllegalStateException("테스트 영상 생성 실패: " + output);
        }
        return video;
    }
}
