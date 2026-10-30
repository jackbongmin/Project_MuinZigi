package com.mujinjigi.domain.video.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 1단계 · 02 (프레임 추출과 고르기)
 * Jev에 보낼 프레임을 고름. 움직밍이 있는 프레임의 앞뒤 몇 초까지 남기고, 오래 조용한 구간은 뺌
 * 변화가 큰 프레임만 고르면 제자리에서 손만 움직이는 동작을 노려서 이렇게 정함
 */

@Component
public class FrameSelector {

    private final double sceneThreshold;
    private final int windowSeconds;

    public FrameSelector(@Value("${app.frame.scene-threshold}") double sceneThreshold,
                         @Value("${app.frame.window-seconds}") int windowSeconds) {
        this.sceneThreshold = sceneThreshold;
        this.windowSeconds = windowSeconds;
    }

    /**
     * @param frames 1초 간격으로 뽑은 프레임(시각 순서)
     * @return 남길 프레임
     */
    public List<ExtractedFrame> select(List<ExtractedFrame> frames) {
        List<ExtractedFrame> selected = new ArrayList<>();
        for (int i = 0; i < frames.size(); i++){
            if(hasMotionNear(frames, i)){
                selected.add(frames.get(i));
            }
        }
        return selected;
    }

    /**
     * i번째 프레임의 앞뒤 windowSeconds장 안에 움직임이 있는 프레임이 있는가 (1초에 1장이라 장 수 = 초)
     */
    private boolean hasMotionNear(List<ExtractedFrame> frames, int i ) {
        int from = Math.max(0, i - windowSeconds);
        int to = Math.min(frames.size()-1, i + windowSeconds);
        for(int j = from; j<=to; j++) {
            if(frames.get(j).sceneScore() > sceneThreshold){
                return true;
            }
        }
        return false;
    }
}
