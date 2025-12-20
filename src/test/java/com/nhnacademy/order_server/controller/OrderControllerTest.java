package com.nhnacademy.order_server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest.OrderItemRequest;
import com.nhnacademy.order_server.dto.request.OrderGuestLoginRequest;
import com.nhnacademy.order_server.dto.response.*;
import com.nhnacademy.order_server.entity.enums.DeliveryStatus;
import com.nhnacademy.order_server.service.OrderService;
import com.nhnacademy.order_server.service.WrapperService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
    private WrapperService wrapperService;

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    // 1. 주문 생성
    @Test
    @DisplayName("[POST] 주문 생성 성공 (201 Created)")
    void createOrder() throws Exception {
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

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(1L))
                .andDo(print());
    }

/*    // 2. 결제 완료
    @Test
    @DisplayName("[POST] 결제 완료 처리 성공 (200 OK)")
    void paymentSuccess() throws Exception {
        Long orderId = 1L;
        String paymentKey = "toss_payment_key_xyz";

        mockMvc.perform(post("/api/orders/{orderId}/payments", orderId)
                        .param("paymentKey", paymentKey)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(print());

        verify(orderService).paymentSuccess(eq(orderId), eq(paymentKey));
    }*/

    // 3. 결제 검증 정보 조회 (String Key 수정 반영)
    @Test
    @DisplayName("[GET] 결제 검증 정보 조회 성공 (200 OK)")
    void getPaymentInfo() throws Exception {
        String orderKey = "test-uuid-1234"; // [수정] String 타입 사용
        OrderValidationInfoResponse response = OrderValidationInfoResponse.builder()
                .orderId(1L)
                .paymentAmount(30000)
                .orderKey(orderKey)
                .userId(100L)
                .usedPoint(1000)
                .build();

        // [수정] String 타입으로 Mocking
        given(orderService.getValidationInfo(eq(orderKey))).willReturn(response);

        mockMvc.perform(get("/api/orders/{orderKey}/payments", orderKey) // PathVariable도 String
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderKey").value(orderKey))
                .andExpect(jsonPath("$.paymentAmount").value(30000))
                .andDo(print());
    }

    // 4. 회원 주문 목록 조회
    @Test
    @DisplayName("[GET] 내 주문 목록 조회 (200 OK)")
    void getMyOrders() throws Exception {
        Long userId = 100L;
        OrderResponse orderRes = OrderResponse.builder()
                .orderId(1L)
                .status(DeliveryStatus.PENDING.name())
                .totalAmount(15000)
                .build();
        Page<OrderResponse> pageResponse = new PageImpl<>(List.of(orderRes));

        given(orderService.getMyOrders(eq(userId), any(Pageable.class))).willReturn(pageResponse);

        mockMvc.perform(get("/api/orders")
                        .header("X-USER-ID", userId)
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderId").value(1L))
                .andDo(print());
    }

    // 5. 주문 상세 조회
    @Test
    @DisplayName("[GET] 주문 상세 조회 (200 OK)")
    void getOrderDetail() throws Exception {
        Long orderId = 1L;
        OrderResponse response = OrderResponse.builder()
                .orderId(orderId)
                .status("SHIPPING")
                .build();

        given(orderService.getOrderDetail(eq(orderId))).willReturn(response);

        mockMvc.perform(get("/api/orders/{orderId}", orderId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.status").value("SHIPPING"))
                .andDo(print());
    }

    // 6. 비회원 주문 조회
    @Test
    @DisplayName("[POST] 비회원 주문 조회 (200 OK)")
    void getGuestOrder() throws Exception {
        OrderGuestLoginRequest request = new OrderGuestLoginRequest();
        ReflectionTestUtils.setField(request, "orderId", 1L);
        ReflectionTestUtils.setField(request, "password", 1234);

        OrderResponse response = OrderResponse.builder()
                .orderId(1L)
                .status("COMPLETED")
                .build();

        given(orderService.getGuestOrder(eq(1L), eq(1234))).willReturn(response);

        mockMvc.perform(post("/api/orders/guests/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1L))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andDo(print());
    }
}