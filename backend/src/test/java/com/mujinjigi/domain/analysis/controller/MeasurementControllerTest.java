package com.mujinjigi.domain.analysis.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import com.mujinjigi.domain.video.TestVideos;

/**
 * 1단계 · 05 (측정 로그)
 * 측정 로그 API 테스트. 프레임 추출·판단을 실제로 돌린 뒤 단계마다 측정 로그가 남았는지 조회해서 확인함 (Jev는 가짜)
 * 확인하는 것: 추출·판단마다 한 줄, 재시도 수 집계, Jev 실패해도 실패 로그 남김, 없는 영상 404
 */
@SpringBootTest(properties = {
        "app.storage.video-dir=build/test-uploads",
        "app.storage.frame-dir=build/test-frames"})
@AutoConfigureMockMvc
@Transactional
class MeasurementControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JevClient jevClient;

    @TempDir
    Path tempDir;

    @Test
    void 추출과_판단을_하면_단계마다_측정_로그가_남는다() throws Exception {
        // 프레임마다 한 번에 성공함
        givenJevSucceedsAfter(1);
        long videoId = uploadAndExtract();
        mockMvc.perform(post("/api/videos/{id}/judgments", videoId)).andExpect(status().isCreated());

        // 테스트 영상은 6초 → 1초에 1장씩 6장을 뽑고 5장을 고름. 5장을 판단함 (장당 2,803토큰)
        mockMvc.perform(get("/api/videos/{id}/measurements", videoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].step").value("FRAME_EXTRACTION"))
                .andExpect(jsonPath("$[0].success").value(true))
                .andExpect(jsonPath("$[0].extractedFrameCount").value(6))
                .andExpect(jsonPath("$[0].frameCount").value(5))
                .andExpect(jsonPath("$[0].settings.sceneThreshold").value(0.005))
                .andExpect(jsonPath("$[0].settings.windowSeconds").value(2))
                .andExpect(jsonPath("$[1].step").value("JUDGMENT"))
                .andExpect(jsonPath("$[1].success").value(true))
                .andExpect(jsonPath("$[1].frameCount").value(5))
                .andExpect(jsonPath("$[1].jevCallCount").value(5))
                .andExpect(jsonPath("$[1].retryCount").value(0))
                .andExpect(jsonPath("$[1].inputTokens").value(5 * 2803))
                .andExpect(jsonPath("$[1].settings.questionSetVersion").value(FrameQuestions.VERSION));
    }

    @Test
    void 재시도한_만큼_Jev_호출_수와_재시도_수가_늘어난다() throws Exception {
        // 프레임마다 529로 한 번 실패하고 두 번째에 성공함
        givenJevSucceedsAfter(2);
        long videoId = uploadAndExtract();
        mockMvc.perform(post("/api/videos/{id}/judgments", videoId)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/videos/{id}/measurements", videoId))
                .andExpect(jsonPath("$[1].frameCount").value(5))
                .andExpect(jsonPath("$[1].jevCallCount").value(10))
                .andExpect(jsonPath("$[1].retryCount").value(5));
    }

    @Test
    void Jev가_재시도까지_실패해도_실패한_측정_로그를_남긴다() throws Exception {
        List<JevCallAttempt> failed = List.of(
                new JevCallAttempt(1, LocalDateTime.now(), 529, "overloaded", null, 100),
                new JevCallAttempt(2, LocalDateTime.now(), 529, "overloaded", null, 100),
                new JevCallAttempt(3, LocalDateTime.now(), 529, "overloaded", null, 100));
        when(jevClient.askYesNo(any(byte[].class), eq("jpg"), eq(FrameQuestions.QUESTIONS)))
                .thenThrow(new JevCallException("Jev 호출 실패: 상태 코드 529, 3번 시도", "{}", failed, null));
        long videoId = uploadAndExtract();
        mockMvc.perform(post("/api/videos/{id}/judgments", videoId)).andExpect(status().isBadGateway());

        // 첫 프레임에서 3번 시도하고 멈춤
        mockMvc.perform(get("/api/videos/{id}/measurements", videoId))
                .andExpect(jsonPath("$[1].step").value("JUDGMENT"))
                .andExpect(jsonPath("$[1].success").value(false))
                .andExpect(jsonPath("$[1].frameCount").value(0))
                .andExpect(jsonPath("$[1].jevCallCount").value(3))
                .andExpect(jsonPath("$[1].retryCount").value(2))
                .andExpect(jsonPath("$[1].inputTokens").value(0));
    }

    @Test
    void 없는_영상은_404를_돌려준다() throws Exception {
        mockMvc.perform(get("/api/videos/{id}/measurements", 999_999))
                .andExpect(status().isNotFound());
    }

    // 가짜 Jev가 attempts번째 시도에서 성공한 것처럼 시도 기록을 만들어 돌려주게 정해 둠
    private void givenJevSucceedsAfter(int attempts) {
        JevResponse fakeResponse = new JevResponse(
                "jev-1.14.0",
                Map.of("person", new JevResponse.Answer("noul", 0.9)),
                new JevResponse.Usage(2803, 0));
        List<JevCallAttempt> tries = new ArrayList<>();
        for (int i = 1; i < attempts; i++) {
            tries.add(new JevCallAttempt(i, LocalDateTime.now(), 529, "overloaded", null, 100));
        }
        tries.add(new JevCallAttempt(attempts, LocalDateTime.now(), 200, "{}", null, 300));
        when(jevClient.askYesNo(any(byte[].class), eq("jpg"), eq(FrameQuestions.QUESTIONS)))
                .thenReturn(new JevResult(fakeResponse, "{}", tries));
    }

    // 테스트 영상을 올리고 프레임까지 추출한 뒤 영상 id를 돌려줌
    private long uploadAndExtract() throws Exception {
        Path video = TestVideos.blackThenWhite(tempDir);
        MockMultipartFile file = new MockMultipartFile("file", "test.mp4", "video/mp4", Files.readAllBytes(video));

        String body = mockMvc.perform(multipart("/api/videos").file(file))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long videoId = ((Number) JsonPath.read(body, "$.id")).longValue();
        mockMvc.perform(post("/api/videos/{id}/frames", videoId)).andExpect(status().isCreated());
        return videoId;
    }
}
