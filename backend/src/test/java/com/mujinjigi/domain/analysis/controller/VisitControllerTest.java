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
import com.mujinjigi.domain.analysis.jev.JevClient;
import com.mujinjigi.domain.analysis.jev.JevResponse;
import com.mujinjigi.domain.analysis.jev.JevResult;
import com.mujinjigi.domain.video.TestVideos;

/**
 * 2단계 · 08 (방문 나누기)
 * 방문 API를 실제 순서(영상 업로드 → 프레임 추출 → 가짜 Jev 판단 → 방문 나누기)대로 불러 보는 테스트
 * 저장·조회와 오류 코드(404, 409)를 확인함. 자르는 규칙 자체는 VisitSplitterTest에서 확인함
 */
@SpringBootTest(properties = {
        "app.storage.video-dir=build/test-uploads",
        "app.storage.frame-dir=build/test-frames"})
@AutoConfigureMockMvc
@Transactional
class VisitControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JevClient jevClient;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // 모든 프레임에 사람이 보인다고 답함 (person 0.9)
        JevResponse fakeResponse = new JevResponse(
                "jev-1.14.0",
                Map.of("person", new JevResponse.Answer("noul", 0.9)),
                new JevResponse.Usage(2803, 0));
        JevResult fakeResult = new JevResult(fakeResponse, "{}",
                List.of(new JevCallAttempt(1, LocalDateTime.now(), 200, "{}", null, 300)));
        when(jevClient.askYesNo(any(byte[].class), eq("jpg"), eq(FrameQuestions.QUESTIONS)))
                .thenReturn(fakeResult);
    }

    @Test
    void 판단_결과로_방문을_나눠_저장하고_돌려준다() throws Exception {
        long videoId = uploadExtractAndJudge();

        // 테스트 영상은 프레임 5장(1~5초)이고 모두 사람이 보임 → 방문 1개
        mockMvc.perform(post("/api/videos/{id}/visits", videoId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].startSec").value(1.0))
                .andExpect(jsonPath("$[0].endSec").value(5.0))
                .andExpect(jsonPath("$[0].frameCount").value(5));

        mockMvc.perform(get("/api/videos/{id}/visits", videoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void 다시_나누면_이전_방문을_지우고_새로_저장한다() throws Exception {
        long videoId = uploadExtractAndJudge();
        mockMvc.perform(post("/api/videos/{id}/visits", videoId)).andExpect(status().isCreated());

        mockMvc.perform(post("/api/videos/{id}/visits", videoId)).andExpect(status().isCreated());

        // 두 번 나눴지만 방문은 1개만 남음
        mockMvc.perform(get("/api/videos/{id}/visits", videoId))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void 판단하지_않은_영상은_409를_돌려준다() throws Exception {
        long videoId = upload();

        mockMvc.perform(post("/api/videos/{id}/visits", videoId))
                .andExpect(status().isConflict());
    }

    @Test
    void 없는_영상은_404를_돌려준다() throws Exception {
        mockMvc.perform(post("/api/videos/{id}/visits", 999_999))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/videos/{id}/visits", 999_999))
                .andExpect(status().isNotFound());
    }

    // 테스트 영상을 올리고 프레임 추출과 판단까지 마친 뒤 영상 id를 돌려줌
    private long uploadExtractAndJudge() throws Exception {
        long videoId = upload();
        mockMvc.perform(post("/api/videos/{id}/frames", videoId)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/videos/{id}/judgments", videoId)).andExpect(status().isCreated());
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
