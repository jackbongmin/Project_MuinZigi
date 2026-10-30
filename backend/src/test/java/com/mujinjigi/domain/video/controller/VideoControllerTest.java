package com.mujinjigi.domain.video.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 1단계 · 01 (영상 업로드 API)
 * 영상 업로드 API 테스트. 서버를 실제로 띄우지 않고 MockMvc로 요청을 흉내 냄
 * DB는 Docker PostgreSQL을 쓰고, 테스트가 끝나면 @Transactional로 저장한 내용을 되돌림
 */
@SpringBootTest(properties = "app.storage.video-dir=build/test-uploads")
@AutoConfigureMockMvc
@Transactional
public class VideoControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void 영상을_올리면_201과_영상_정보를_돌려준다() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "P1.mp4", "video/mp4", new byte[1024]);

        mockMvc.perform(multipart("/api/videos").file(file))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.originalName").value("P1.mp4"))
                .andExpect(jsonPath("$.sizeBytes").value(1024));
    }

    @Test
    void 영상이_아닌_파일은_400을_돌려준다() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "memo.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/videos").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 빈_파일은_400을_돌려준다() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "P1.mp4", "video/mp4", new byte[0]);

        mockMvc.perform(multipart("/api/videos").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 없는_영상을_조회하면_404를_돌려준다() throws Exception {
        mockMvc.perform(get("/api/videos/{id}", 999_999))
                .andExpect(status().isNotFound());
    }

}
