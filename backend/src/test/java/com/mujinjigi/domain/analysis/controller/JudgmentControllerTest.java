package com.mujinjigi.domain.analysis.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.mujinjigi.domain.analysis.jev.FrameQuestions;
import com.mujinjigi.domain.analysis.jev.JevClient;
import com.mujinjigi.domain.analysis.jev.JevResponse;
import com.mujinjigi.domain.video.TestVideos;

/**
 * 1단계 · 03 (Jev 프레임 판단)
 * 프레임 판단 API 테스트. 진짜 Jev 대신 가짜(JevClient mock)가 정해 둔 답을 돌려줌 (비용·네트워크 없이 실행)
 */
@SpringBootTest(properties = {
        "app.storage.video-dir=build/test-uploads",
        "app.storage.frame-dir=build/test-frames"})
@AutoConfigureMockMvc
@Transactional
class JudgmentControllerTest {

    @Autowired
    MockMvc mockMvc;

    // 스프링이 진짜 JevClient 대신 이 가짜를 주입함
    @MockitoBean
    JevClient jevClient;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // 어떤 이미지를 보내도 같은 답을 돌려주게 정해 둠
        JevResponse fakeResponse = new JevResponse(
                "jev-1.14.0",
                Map.of("person", new JevResponse.Answer("noul", 0.9),
                        "putting_in_bag", new JevResponse.Answer("noul", 0.1)),
                new JevResponse.Usage(2803, 0));
        when(jevClient.askYesNo(any(byte[].class), eq("jpg"), eq(FrameQuestions.QUESTIONS)))
                .thenReturn(fakeResponse);
    }

    @Test
    void 프레임을_판단하면_결과를_저장하고_돌려준다() throws Exception {
        long videoId = uploadAndExtract();

        // 테스트 영상은 프레임 5장(1~5초) → Jev 5번 호출
        mockMvc.perform(post("/api/videos/{id}/judgments", videoId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].timeSec").value(1.0))
                .andExpect(jsonPath("$[0].questionSetVersion").value("v2"))
                .andExpect(jsonPath("$[0].model").value("jev-1.14.0"))
                .andExpect(jsonPath("$[0].inputTokens").value(2803))
                .andExpect(jsonPath("$[0].answers.person").value(0.9));
        verify(jevClient, times(5)).askYesNo(any(byte[].class), eq("jpg"), eq(FrameQuestions.QUESTIONS));

        mockMvc.perform(get("/api/videos/{id}/judgments", videoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void 다시_판단하면_이미_판단한_프레임은_건너뛴다() throws Exception {
        long videoId = uploadAndExtract();
        mockMvc.perform(post("/api/videos/{id}/judgments", videoId)).andExpect(status().isCreated());

        mockMvc.perform(post("/api/videos/{id}/judgments", videoId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(5));
        // 두 번 불렀지만 Jev 호출은 처음의 5번뿐임
        verify(jevClient, times(5)).askYesNo(any(byte[].class), eq("jpg"), eq(FrameQuestions.QUESTIONS));
    }

    @Test
    void 프레임을_추출하지_않은_영상은_409를_돌려준다() throws Exception {
        long videoId = upload();

        mockMvc.perform(post("/api/videos/{id}/judgments", videoId))
                .andExpect(status().isConflict());
    }

    @Test
    void 없는_영상은_404를_돌려준다() throws Exception {
        mockMvc.perform(post("/api/videos/{id}/judgments", 999_999))
                .andExpect(status().isNotFound());
    }

    // 테스트 영상을 올리고 프레임까지 추출한 뒤 영상 id를 돌려줌
    private long uploadAndExtract() throws Exception {
        long videoId = upload();
        mockMvc.perform(post("/api/videos/{id}/frames", videoId)).andExpect(status().isCreated());
        return videoId;
    }

    private long upload() throws Exception {
        Path video = TestVideos.blackThenWhite(tempDir);
        MockMultipartFile file = new MockMultipartFile("file", "test.mp4", "video/mp4", Files.readAllBytes(video));

        String body = mockMvc.perform(multipart("/api/videos").file(file))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.id");
        return id.longValue();
    }
}