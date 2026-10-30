package com.mujinjigi.domain.video.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 프레임 고르기 규칙 테스트. Spring 없이 객체를 직접 만들어 빠르게 확인함
 */
class FrameSelectorTest {

    // 기준 0.005, 앞뒤 2초 (application.properties와 같은 값)
    private final FrameSelector selector = new FrameSelector(0.005, 2);

    @Test
    void 움직임이_없으면_아무_프레임도_고르지_않는다() {
        List<ExtractedFrame> frames = framesWithScores(0, 0.001, 0.002, 0.005, 0.003);

        assertThat(selector.select(frames)).isEmpty();
    }

    @Test
    void 움직임이_있으면_앞뒤_2초까지_고른다() {
        // 5초에만 움직임 → 3~7초를 고름
        List<ExtractedFrame> frames = framesWithScores(0, 0, 0, 0, 0, 0.05, 0, 0, 0, 0, 0);

        assertThat(selector.select(frames))
                .extracting(ExtractedFrame::timeSec)
                .containsExactly(3.0, 4.0, 5.0, 6.0, 7.0);
    }

    @Test
    void 움직임_사이가_가까우면_조용한_프레임도_남긴다() {
        // 1초와 5초에 움직임 → 사이 2~4초도 남음 (제자리에서 손만 움직이는 구간)
        List<ExtractedFrame> frames = framesWithScores(0, 0.03, 0.001, 0.001, 0.001, 0.03, 0, 0, 0, 0);

        assertThat(selector.select(frames))
                .extracting(ExtractedFrame::timeSec)
                .containsExactly(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0);
    }

    @Test
    void 영상_처음과_끝에서도_범위를_벗어나지_않는다() {
        List<ExtractedFrame> frames = framesWithScores(0.05, 0, 0, 0, 0.05);

        assertThat(selector.select(frames)).hasSize(5);
    }

    // 점수 목록으로 0초부터 1초 간격의 프레임을 만듦
    private List<ExtractedFrame> framesWithScores(double... scores) {
        List<ExtractedFrame> frames = new ArrayList<>();
        for (int i = 0; i < scores.length; i++) {
            frames.add(new ExtractedFrame(String.format("frame_%04d.jpg", i + 1), i, scores[i]));
        }
        return frames;
    }
}
