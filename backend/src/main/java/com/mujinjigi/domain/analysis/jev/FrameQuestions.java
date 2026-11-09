package com.mujinjigi.domain.analysis.jev;


import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 1단계 · 03 (Jev 프레임 판단)
 * 프레임 하나에 Jev로 보내는 질문 세트. 0단계 실험에서 고른 vs를 그대로 쓸거임
 * 질문 문구를 바꾸면 VERSION도 올려서, 어떤 질문으로 나온 결과인지 DB에서 구분되게 함
 */
public final class FrameQuestions {

    public static final String VERSION = "v2";

    public static final Map<String, String> QUESTIONS;

    static {
        Map<String, String> questions = new LinkedHashMap<>();
        questions.put("person", "Is there a person or part of a person (such as a hand) in the image `photo`?");
        questions.put("holding_product", "Is a person holding a store product (not a shopping bag, phone or wallet) in their hand in the image `photo`?");
        questions.put("putting_in_bag", "Is a person putting an item into a bag or pocket in the image `photo`? Putting a phone, wallet or other personal belonging into a bag, or taking one out, does not count.");
        questions.put("paying", "Is a person paying (by card, phone or cash) at a checkout or payment terminal in the image `photo`?");

        // 밖에서 질문을 바꾸지 못하게 읽기전용으로 감쌈
        QUESTIONS = Collections.unmodifiableMap(questions);
    }

    private FrameQuestions() {

    }

}

