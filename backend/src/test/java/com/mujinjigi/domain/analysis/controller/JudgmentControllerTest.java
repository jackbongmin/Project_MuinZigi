package com.mujinjigi.domain.analysis.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.LocalDateTime;
import java.util.List;
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
import com.mujinjigi.domain.analysis.jev.JevCallAttempt;
import com.mujinjigi.domain.analysis.jev.JevCallException;
import com.mujinjigi.domain.analysis.jev.JevClient;
import com.mujinjigi.domain.analysis.jev.JevResponse;
import com.mujinjigi.domain.analysis.jev.JevResult;
import com.mujinjigi.domain.analysis.repository.JevCallLogRepository;
import com.mujinjigi.domain.video.TestVideos;

/**
 * 1단계 · 03 (Jev 프레임 판단)
 * 1단계 · 04 (호출 기록, Jev 실패 시 502)
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

    // 시도 기록이 저장됐는지 셀 때 씀
    @Autowired
    JevCallLogRepository jevCallLogRepository;

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
        // 한 번에 성공한 것처럼 시도 기록 1개를 같이 돌려줌
        JevResult fakeResult = new JevResult(fakeResponse, "{\"model\": \"jev-1.14.0\"}",
                List.of(new JevCallAttempt(1, LocalDateTime.now(), 200, "{\"model\": \"jev-1.14.0\"}", null, 300)));
        when(jevClient.askYesNo(any(byte[].class), eq("jpg"), eq(FrameQuestions.QUESTIONS)))
                .thenReturn(fakeResult);
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
        // 프레임마다 시도 기록이 1줄씩 남음
        assertThat(jevCallLogRepository.countByFrameVideoId(videoId)).isEqualTo(5);
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
    void Jev가_재시도까지_실패하면_502를_돌려주고_시도_기록을_남긴다() throws Exception {
        List<JevCallAttempt> failed = List.of(
                new JevCallAttempt(1, LocalDateTime.now(), 529, "overloaded", null, 100),
                new JevCallAttempt(2, LocalDateTime.now(), 529, "overloaded", null, 100),
                new JevCallAttempt(3, LocalDateTime.now(), 529, "overloaded", null, 100));
        when(jevClient.askYesNo(any(byte[].class), eq("jpg"), eq(FrameQuestions.QUESTIONS)))
                .thenThrow(new JevCallException("Jev 호출 실패: 상태 코드 529, 3번 시도", "{}", failed, null));
        long videoId = uploadAndExtract();

        mockMvc.perform(post("/api/videos/{id}/judgments", videoId))
                .andExpect(status().isBadGateway());
        assertThat(jevCallLogRepository.countByFrameVideoId(videoId)).isEqualTo(3);
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
