package com.mujinjigi.domain.video.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.mujinjigi.domain.video.TestVideos;

/**
 * 1단계 · 02 (프레임 추출과 고르기)
 * 프레임 추출 API 테스트. 실제로 영상을 올리고 FFmpeg로 프레임을 뽑음 (PATH에 ffmpeg가 있어야 함)
 */
@SpringBootTest(properties = {
        "app.storage.video-dir=build/test-uploads",
        "app.storage.frame-dir=build/test-frames"})
@AutoConfigureMockMvc
@Transactional
class VideoFrameControllerTest {

    @Autowired
    MockMvc mockMvc;

    @TempDir
    Path tempDir;

    @Test
    void 프레임을_추출하면_움직임_주변_프레임만_돌려준다() throws Exception {
        long videoId = uploadTestVideo();

        // 3초에만 장면 변화 → 앞뒤 2초인 1~5초 5장 (0초는 빠짐)
        mockMvc.perform(post("/api/videos/{id}/frames", videoId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].timeSec").value(1.0))
                .andExpect(jsonPath("$[4].timeSec").value(5.0));

        mockMvc.perform(get("/api/videos/{id}/frames", videoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void 같은_영상을_두_번_추출하면_409를_돌려준다() throws Exception {
        long videoId = uploadTestVideo();
        mockMvc.perform(post("/api/videos/{id}/frames", videoId)).andExpect(status().isCreated());

        mockMvc.perform(post("/api/videos/{id}/frames", videoId))
                .andExpect(status().isConflict());
    }

    @Test
    void 없는_영상의_프레임을_추출하면_404를_돌려준다() throws Exception {
        mockMvc.perform(post("/api/videos/{id}/frames", 999_999))
                .andExpect(status().isNotFound());
    }

    // 테스트 영상을 만들어 업로드 API로 올리고 영상 id를 돌려줌
    private long uploadTestVideo() throws Exception {
        Path video = TestVideos.blackThenWhite(tempDir);
        MockMultipartFile file = new MockMultipartFile("file", "test.mp4", "video/mp4", Files.readAllBytes(video));

        String body = mockMvc.perform(multipart("/api/videos").file(file))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        // 응답 JSON에서 id만 꺼냄
        Number id = JsonPath.read(body, "$.id");
        return id.longValue();
    }
}
