package com.nhnacademy.order_server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest.OrderItemRequest;
import com.nhnacademy.order_server.dto.response.OrderCreateResponse;
import com.nhnacademy.order_server.dto.response.OrderValidationInfoResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
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

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @Test
    @DisplayName("[POST] 주문 생성 성공 (201 Created)")
    void createOrder() throws Exception {
        // given
        OrderCreateRequest request = new OrderCreateRequest();
        ReflectionTestUtils.setField(request, "userId", 1L);
        ReflectionTestUtils.setField(request, "receiverName", "홍길동");
        ReflectionTestUtils.setField(request, "receiverAddress", "서울시 강남구");

        OrderItemRequest item = new OrderItemRequest();
        ReflectionTestUtils.setField(item, "bookId", 101L);
        ReflectionTestUtils.setField(item, "quantity", 2);
        ReflectionTestUtils.setField(request, "orderItems", List.of(item));

        OrderCreateResponse response = OrderCreateResponse.builder()
                .orderId(1L)
                .orderKey("test-uuid-1234")
                .totalAmount(30000)
                .build();

        given(orderService.createOrder(any(OrderCreateRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(1L))
                .andExpect(jsonPath("$.orderKey").value("test-uuid-1234"))
                .andExpect(jsonPath("$.totalAmount").value(30000))
                .andDo(print());
    }

    @Test
    @DisplayName("[POST] 결제 완료 처리 성공 (200 OK)")
    void paymentSuccess() throws Exception {
        // given
        Long orderId = 1L;
        String paymentKey = "toss_payment_key_xyz";

        // when & then
        // RESTful URL: /api/orders/{orderId}/payments
        mockMvc.perform(post("/api/orders/{orderId}/payments", orderId)
                        .param("paymentKey", paymentKey)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(print());

        // Verify Service Call
        verify(orderService).paymentSuccess(eq(orderId), eq(paymentKey));
    }

    @Test
    @DisplayName("[GET] 결제 검증 정보 조회 성공 (200 OK)")
    void getPaymentInfo() throws Exception {
        // given
        Long orderId = 1L;
        OrderValidationInfoResponse response = OrderValidationInfoResponse.builder()
                .orderId(orderId)
                .paymentAmount(30000) // DTO 필드명 수정 반영 (realAmount -> paymentAmount)
                .orderKey("test-uuid-1234")
                .userId(100L)         // DTO 필드명 수정 반영 (memberId -> userId)
                .build();

        given(orderService.getValidationInfo(eq(orderId))).willReturn(response);

        // when & then
        // RESTful URL: /api/orders/{orderId}/payments
        mockMvc.perform(get("/api/orders/{orderId}/payments", orderId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.paymentAmount").value(30000))
                .andExpect(jsonPath("$.orderKey").value("test-uuid-1234"))
                .andExpect(jsonPath("$.userId").value(100L))
                .andDo(print());
    }
}