package com.mujinjigi.domain.analysis.jev;


import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 1단계 · 03 (Jev 프레임 판단)
 * 프레임 한 장마다 Jev에 같이 보내는 질문 4개(질문 이름 → 영어 문장)를 모아 둔 클래스
 * 예: "person" → 사람(또는 손 같은 몸 일부)이 보이나, "putting_in_bag" → 가방·주머니에 물건을 넣고 있나
 * 질문 문구를 바꾸면 VERSION(지금 v2)도 올려서, 어떤 질문으로 나온 결과인지 DB에서 구분되게 함
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

