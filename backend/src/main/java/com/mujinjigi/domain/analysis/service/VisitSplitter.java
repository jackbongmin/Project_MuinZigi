package com.mujinjigi.domain.analysis.service;


import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 2단계 · 08 (방문 나누기)
 * 프레임별 "사람 있음" 확률을 시간순으로 보고, 손님 한 명이 머문 구간(방문)으로 잘라 주는 클래스
 * 예: 11~19초에 사람이 보이고 300~340초에 또 보이면 → 방문 2개 [11~19초], [300~340초]
 * 사람이 5초 넘게 안 보이면 거기서 자름 (설정 app.visit.gap-seconds, 결정 006). 계산에 Spring·DB가 필요 없음
 */

@Component
public class VisitSplitter {

    private final double personThreshold;
    private final double gapSeconds;

    public VisitSplitter(@Value("${app.visit.person-threshold}") double personThreshold,
                         @Value("${app.visit.gap-seconds}") double gapSecond) {
        this.personThreshold = personThreshold;
        this.gapSeconds = gapSecond;
    }

    /**
     * @param frames 판단을 마친 프레임(시간 순서)
     * @return 방문 목록(시각 순서). 사람이 한번도 안보이면 빈 목록
     */
    public List<VisitRange> split(List<PersonFrame> frames) {
        List<VisitRange> visits = new ArrayList<>();
        double start = 0;
        double last = 0;
        int count = 0;      // 지금 이어 붙이는 방문의 프레임 수. 0이면 진행중인 방문이 없음

        for(PersonFrame frame : frames) {
            if(frame.personProbability() < personThreshold) {
                continue;   // 사람이 안보이는 프레임은 건너뜀. 공백은 아래에서 시각 차이로 잼
            }

            // 마지막으로 보인 시각과의 차이가 gapSeconds보다 크면 앞 방문을 닫고 새방문을 시작함
            // 프레임이 없는 시간(활동 구간 밖)도 시각 창에 들어가므로 "안보인 것"으로 계산됨
            if(count>0&&frame.timeSec() - last > gapSeconds) {
                visits.add(new VisitRange(start, last, count));
                count = 0;
            }

            if (count == 0) {
                start = frame.timeSec();
            }
            last = frame.timeSec();
            count ++;
        }

        // 마지막 방문은 반복문 안에서 닫히지 않으므로 여기서 넣음
        if(count > 0) {
            visits.add(new VisitRange(start, last, count));
        }
        return visits;
    }
}
