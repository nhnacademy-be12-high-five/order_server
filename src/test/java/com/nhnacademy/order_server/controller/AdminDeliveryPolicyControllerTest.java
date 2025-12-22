package com.nhnacademy.order_server.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.order_server.dto.request.DeliveryPolicyRequest;
import com.nhnacademy.order_server.dto.response.DeliveryPolicyResponse;
import com.nhnacademy.order_server.service.DeliveryPolicyService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminDeliveryPolicyController.class)
@TestPropertySource(properties = { // [추가] URL 설정 문제 예방용
        "book.service.url=http://localhost:8081",
        "coupon.service.url=http://localhost:8082",
        "member.service.url=http://localhost:8083",
        "cart.service.url=http://localhost:8084",
        "payment.service.url=http://localhost:8085"
})
class AdminDeliveryPolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DeliveryPolicyService deliveryPolicyService;

    // [해결책] RedisConnectionFactory를 Mock으로 주입하여 설정 통과
    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @Test
    @DisplayName("배송 정책 등록 성공 (201 Created)")
    void createDeliveryPolicy() throws Exception {
        // given
        DeliveryPolicyRequest request = new DeliveryPolicyRequest();
        ReflectionTestUtils.setField(request, "standardShippingFee", 3000);
        ReflectionTestUtils.setField(request, "minOrderAmount", 30000);

        // when & then
        mockMvc.perform(post("/api/admin/delivery-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
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
                .andExpect(jsonPath("$.standardShippingFee").value(3000))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @DisplayName("정책 삭제(비활성화) 성공 (204 No Content)")
    void deleteDeliveryPolicy() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/admin/delivery-policies/{policyId}", 1L))
                .andExpect(status().isNoContent());
    }
}