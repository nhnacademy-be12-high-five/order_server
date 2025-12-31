package com.nhnacademy.order_server.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.order_server.dto.request.WrapperRegisterRequest;
import com.nhnacademy.order_server.dto.response.WrapperResponse;
import com.nhnacademy.order_server.service.WrapperService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminWrapperController.class)
@TestPropertySource(properties = {
        "book.service.url=http://localhost:8081",
        "coupon.service.url=http://localhost:8082",
        "member.service.url=http://localhost:8083",
        "cart.service.url=http://localhost:8084",
        "payment.service.url=http://localhost:8085"
})
class AdminWrapperControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WrapperService wrapperService;

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @Test
    @DisplayName("포장지 등록 성공 (201 Created)")
    void createWrapper() throws Exception {
        // given
        WrapperRegisterRequest request = new WrapperRegisterRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "wrapperName", "선물 포장");
        org.springframework.test.util.ReflectionTestUtils.setField(request, "wrapperPrice", 1000);

        // when & then
        mockMvc.perform(post("/api/admin/wrappers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("포장지 목록 조회 성공 (200 OK)")
    void getAllWrappers() throws Exception {
        // given
        WrapperResponse response = WrapperResponse.builder()
                .id(1L)
                .name("선물 포장")
                .price(1000)
                .build();

        given(wrapperService.getAllWrappers()).willReturn(List.of(response));

        // when & then
        mockMvc.perform(get("/api/admin/wrappers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("선물 포장")); // 리스트 첫 번째 요소 확인
    }

    @Test
    @DisplayName("포장지 수정 성공 (200 OK)")
    void updateWrapper() throws Exception {
        // given
        WrapperRegisterRequest request = new WrapperRegisterRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "wrapperName", "수정된 포장");
        org.springframework.test.util.ReflectionTestUtils.setField(request, "wrapperPrice", 2000);

        // when & then
        mockMvc.perform(put("/api/admin/wrappers/{wrapperId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("포장지 삭제 성공 (204 No Content)")
    void deleteWrapper() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/admin/wrappers/{wrapperId}", 1L))
                .andExpect(status().isNoContent());
    }
}