package com.nhnacademy.order_server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest.OrderItemRequest;
import com.nhnacademy.order_server.dto.response.OrderCreateResponse;
import com.nhnacademy.order_server.service.OrderService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@TestPropertySource(properties = {
        "book.service.url=http://localhost:8081",
        "coupon.service.url=http://localhost:8082",
        "member.service.url=http://localhost:8083",
        "cart.service.url=http://localhost:8084",
        "payment.service.url=http://localhost:8085"
})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    // [필수] RedisConfig 로딩 에러 방지용 가짜 객체
    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @Test
    @DisplayName("주문 생성 요청 성공 (201 Created)")
    void createOrder() throws Exception {
        // given
        OrderCreateRequest request = new OrderCreateRequest();

        // DTO에 Setter가 없으므로 ReflectionTestUtils로 값 주입
        ReflectionTestUtils.setField(request, "userId", 1L);
        ReflectionTestUtils.setField(request, "receiverName", "홍길동");
        ReflectionTestUtils.setField(request, "receiverAddress", "서울시 강남구");

        OrderItemRequest item = new OrderItemRequest();
        ReflectionTestUtils.setField(item, "bookId", 101L);
        ReflectionTestUtils.setField(item, "quantity", 2);
        ReflectionTestUtils.setField(request, "orderItems", List.of(item));

        // 서비스가 반환할 응답 Mocking
        OrderCreateResponse response = OrderCreateResponse.builder()
                .orderId(1L)
                .orderKey("test-uuid-1234")
                .orderName("자바의 정석 외 0건")
                .totalAmount(30000)
                .build();

        given(orderService.createOrder(any(OrderCreateRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()) // 201 확인
                .andExpect(jsonPath("$.orderId").value(1L))
                .andExpect(jsonPath("$.orderKey").value("test-uuid-1234"))
                .andExpect(jsonPath("$.totalAmount").value(30000));
    }

    @Test
    @DisplayName("결제 성공 통보 처리 성공 (200 OK)")
    void paymentSuccess() throws Exception {
        // given
        Long orderId = 1L;
        String paymentKey = "toss_payment_key_xyz";

        // when & then
        mockMvc.perform(post("/api/orders/{orderId}/payment-success", orderId)
                        .param("paymentKey", paymentKey) // 쿼리 파라미터로 전송
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()); // 200 확인

        // Verify: 서비스 메서드가 올바른 파라미터로 호출되었는지 검증
        verify(orderService).paymentSuccess(eq(orderId), eq(paymentKey));
    }
}