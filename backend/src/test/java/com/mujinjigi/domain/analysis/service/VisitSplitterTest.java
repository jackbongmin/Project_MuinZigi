package com.mujinjigi.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 2단계 · 08 (방문 나누기)
 * VisitSplitter가 방문을 제대로 자르는지 확인하는 테스트. Spring·DB 없이 바로 실행됨
 * 기준값 바로 위·아래(확률 0.49 / 0.5, 공백 5초 / 6초)에서 결과가 맞는지 봄
 */
class VisitSplitterTest {

    // 판정 기준 0.5, 공백 5초 (application.properties와 같은 값)
    private final VisitSplitter splitter = new VisitSplitter(0.5, 5);

    @Test
    void 사람이_안_보이면_방문이_없다() {
        List<PersonFrame> frames = List.of(
                new PersonFrame(1, 0.1),
                new PersonFrame(2, 0.49),
                new PersonFrame(3, 0.0));

        assertThat(splitter.split(frames)).isEmpty();
    }

    @Test
    void 확률이_판정_기준과_같으면_사람으로_본다() {
        List<PersonFrame> frames = List.of(
                new PersonFrame(1, 0.49),
                new PersonFrame(2, 0.5));

        assertThat(splitter.split(frames)).containsExactly(new VisitRange(2, 2, 1));
    }

    @Test
    void 사람이_이어서_보이면_방문_하나로_묶는다() {
        List<PersonFrame> frames = List.of(
                new PersonFrame(1, 0.9),
                new PersonFrame(2, 0.8),
                new PersonFrame(3, 0.95),
                new PersonFrame(4, 0.7));

        assertThat(splitter.split(frames)).containsExactly(new VisitRange(1, 4, 4));
    }

    @Test
    void 공백이_5초면_같은_방문이다() {
        // 10초 다음에 15초에 다시 보임 → 차이 5초 (11~14초 4장 안 보임) → 이어짐
        List<PersonFrame> frames = List.of(
                new PersonFrame(10, 0.9),
                new PersonFrame(11, 0.1),
                new PersonFrame(12, 0.1),
                new PersonFrame(13, 0.1),
                new PersonFrame(14, 0.1),
                new PersonFrame(15, 0.9));

        assertThat(splitter.split(frames)).containsExactly(new VisitRange(10, 15, 2));
    }

    @Test
    void 공백이_5초를_넘으면_다른_방문이다() {
        // 10초 다음에 16초에 다시 보임 → 차이 6초 (11~15초 5장 안 보임) → 나뉨
        List<PersonFrame> frames = List.of(
                new PersonFrame(9, 0.9),
                new PersonFrame(10, 0.9),
                new PersonFrame(11, 0.1),
                new PersonFrame(12, 0.1),
                new PersonFrame(13, 0.1),
                new PersonFrame(14, 0.1),
                new PersonFrame(15, 0.1),
                new PersonFrame(16, 0.9));

        assertThat(splitter.split(frames)).containsExactly(
                new VisitRange(9, 10, 2),
                new VisitRange(16, 16, 1));
    }

    @Test
    void 프레임이_없는_시간도_안_보인_것으로_본다() {
        // 3초 다음 프레임이 20초 (사이는 활동 구간 밖이라 프레임이 없음) → 나뉨
        List<PersonFrame> frames = List.of(
                new PersonFrame(2, 0.9),
                new PersonFrame(3, 0.9),
                new PersonFrame(20, 0.9),
                new PersonFrame(21, 0.9));

        assertThat(splitter.split(frames)).containsExactly(
                new VisitRange(2, 3, 2),
                new VisitRange(20, 21, 2));
    }
}