package com.mujinjigi.domain.analysis.jev;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 0단계 · 미결제 상황 판단 2차 테스트 (영상 프레임)
 * 질문 세트 v3. v2에 "어디에 넣었나" 질문 3개를 더함
 * 장바구니에 넣으면 정상, 주머니·옷 속에 넣으면 의심도가 높다는 식으로 의심도에 차등을 주려는 목적
 * v2와 같은 실행에서 비교하면 질문 3개를 더했을 때 늘어나는 토큰도 함께 측정됨
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
