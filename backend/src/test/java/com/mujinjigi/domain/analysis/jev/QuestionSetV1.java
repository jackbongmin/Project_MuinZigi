package com.mujinjigi.domain.analysis.jev;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 0단계 · 미결제 상황 판단 2차 테스트 (영상 프레임)
 * 실험용 질문 세트 v1. v0에서 손님이 자기 폰·지갑을 가방에 넣는 것도 "넣음"으로 잡혀서(오탐), 집음·넣음 질문에 "진열대에서 가져온 매장 상품"이라는 조건을 붙인 버전
 * 예: 폰을 가방에 넣는 장면 → v0은 넣음 확률이 높게 나옴, v1은 낮게 나오는지 비교
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
