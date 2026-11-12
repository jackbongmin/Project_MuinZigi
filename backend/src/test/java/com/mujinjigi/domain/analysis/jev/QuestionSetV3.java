package com.mujinjigi.domain.analysis.jev;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 0단계 · 미결제 상황 판단 2차 테스트 (영상 프레임)
 * 실험용 질문 세트 v3. v2에 "어디에 넣었나"(장바구니 / 주머니 / 옷 속) 질문 3개를 더한 버전
 * 예: 장바구니에 넣으면 정상, 주머니·옷 속에 넣으면 의심도를 더 높게 주려는 목적
 * v2와 같이 돌리면 질문 3개가 늘 때 토큰이 얼마나 느는지도 함께 잼
 */
final class QuestionSetV3 {

    static final Map<String, String> QUESTIONS = new LinkedHashMap<>(QuestionSetV2.QUESTIONS);

    static {
        QUESTIONS.put("into_pocket", "Is a person putting an item into a pocket of their clothes in the image `photo`?");
        QUESTIONS.put("into_clothes", "Is a person hiding an item inside their clothes or up their sleeve in the image `photo`?");
        QUESTIONS.put("into_store_basket", "Is a person putting an item into a store shopping basket or cart in the image `photo`?");
    }

    private QuestionSetV3() {
    }
}
