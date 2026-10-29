package com.mujinjigi.domain.analysis.jev;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 질문 세트 v1. v0의 오탐(손님 자기 폰·지갑을 가방에 넣거나 꺼내는 동작을 "넣음"으로 판단)을 줄이려고
 * holding_product와 putting_in_bag에 "진열대에서 가져온 매장 상품"이라는 조건과 제외 대상을 넣음
 * person, paying은 v0과 같게 두어서 바뀐 질문의 효과만 비교함
 */
final class QuestionSetV1 {

    static final Map<String, String> QUESTIONS = new LinkedHashMap<>();

    static {
        QUESTIONS.put("person", QuestionSetV0.QUESTIONS.get("person"));
        QUESTIONS.put("holding_product", "Is a person holding a product taken from a store shelf (not a phone, wallet or other personal belonging) in their hand in the image `photo`?");
        QUESTIONS.put("putting_in_bag", "Is a person putting a product taken from a store shelf into a bag or pocket in the image `photo`? Putting a phone, wallet or other personal belonging into a bag, or taking one out, does not count.");
        QUESTIONS.put("paying", QuestionSetV0.QUESTIONS.get("paying"));
    }

    private QuestionSetV1() {
    }
}
