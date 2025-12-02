package com.nhnacademy.order_server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.order_server.dto.request.DeliveryPolicyRequest;
import com.nhnacademy.order_server.dto.response.DeliveryPolicyResponse;
import com.nhnacademy.order_server.service.DeliveryPolicyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminDeliveryPolicyController.class) // 컨트롤러만 테스트
class AdminDeliveryPolicyControllerTest {

    @Autowired
    private MockMvc mockMvc; // API 요청을 가짜로 날려주는 도구

    @Autowired
    private ObjectMapper objectMapper; // 객체 -> JSON 변환기

    @MockitoBean // 가짜 서비스 (컨트롤러 테스트니까 서비스 로직은 몰라도 됨)
    private DeliveryPolicyService deliveryPolicyService;

    @Test
    @DisplayName("배송 정책 등록 성공 (201 Created)")
    void createDeliveryPolicy() throws Exception {
        // given
        DeliveryPolicyRequest request = new DeliveryPolicyRequest();
        // (ReflectionTestUtils로 값 설정 필요)
        org.springframework.test.util.ReflectionTestUtils.setField(request, "standardShippingFee", 3000);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "minOrderAmount", 30000);

        // when & then
        mockMvc.perform(post("/api/admin/delivery-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))) // JSON 바디 전송
                .andExpect(status().isCreated()); // 201 확인
    }

    @Test
    @DisplayName("활성 정책 조회 성공 (200 OK)")
    void getActivePolicy() throws Exception {
        // given
        DeliveryPolicyResponse response = DeliveryPolicyResponse.builder()
                .id(1L)
                .standardShippingFee(3000)
                .minOrderAmount(30000)
                .isActive(true)
                .effectiveDate(LocalDateTime.now())
                .build();

        given(deliveryPolicyService.getActivePolicy()).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/admin/delivery-policies/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.standardShippingFee").value(3000)) // 응답 JSON 필드 확인
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @DisplayName("정책 삭제(비활성화) 성공 (204 No Content)")
    void deleteDeliveryPolicy() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/admin/delivery-policies/{policyId}", 1L))
                .andExpect(status().isNoContent()); // 204 확인
    }
}