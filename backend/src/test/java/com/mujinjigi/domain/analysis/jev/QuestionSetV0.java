package com.mujinjigi.domain.analysis.jev;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 0단계 질문 세트 v0. 복잡한 질문 하나 대신 단순한 예/아니오 질문 여러 개로 나눔 (TypeSafe 권고)
 * 사진 테스트와 영상 프레임 테스트가 같은 질문을 써야 결과를 비교할 수 있어서 한 곳에 둠
 */
final class QuestionSetV0 {

    static final Map<String, String> QUESTIONS = new LinkedHashMap<>();

    static {
        QUESTIONS.put("person", "Is there a person or part of a person (such as a hand) in the image `photo`?");
        QUESTIONS.put("holding_product", "Is a person holding a store product (not a shopping bag) in their hand in the image `photo`?");
        QUESTIONS.put("putting_in_bag", "Is a person putting an item into a bag or pocket in the image `photo`?");
        QUESTIONS.put("paying", "Is a person paying (by card, phone or cash) at a checkout or payment terminal in the image `photo`?");
    }

    private QuestionSetV0() {
    }
}
