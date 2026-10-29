package com.mujinjigi.domain.analysis.jev;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 질문 세트 v2. v1은 자기 물건 오탐을 없앴지만 진짜 상품을 넣는 장면(P2 7초)도 0.49로 놓침
 * "진열대에서 가져온" 조건은 빼고 "폰·지갑 같은 개인 물건은 해당하지 않음" 제외 문장만 남겨서 균형을 시험함
 */
final class QuestionSetV2 {

    static final Map<String, String> QUESTIONS = new LinkedHashMap<>();

    static {
        QUESTIONS.put("person", QuestionSetV0.QUESTIONS.get("person"));
        QUESTIONS.put("holding_product", "Is a person holding a store product (not a shopping bag, phone or wallet) in their hand in the image `photo`?");
        QUESTIONS.put("putting_in_bag", "Is a person putting an item into a bag or pocket in the image `photo`? Putting a phone, wallet or other personal belonging into a bag, or taking one out, does not count.");
        QUESTIONS.put("paying", QuestionSetV0.QUESTIONS.get("paying"));
    }

    private QuestionSetV2() {
    }
}
