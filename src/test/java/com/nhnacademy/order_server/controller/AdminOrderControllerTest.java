package com.nhnacademy.order_server.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.order_server.dto.request.OrderStatusUpdateRequest;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import com.nhnacademy.order_server.service.AdminOrderService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminOrderController.class)
@AutoConfigureMockMvc(addFilters = false) // [2] 시큐리티 필터 비활성화 (401/403 에러 방지)
class AdminOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminOrderService adminOrderService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/admin/orders - 관리자 주문 목록 조회")
    void getOrders() throws Exception {
        // given
        // [3] orderId -> id 로 변경
        Page<OrderResponse> responsePage = new PageImpl<>(List.of(
                OrderResponse.builder().id(1L).status("PENDING").build()
        ));

        given(adminOrderService.getOrders(any(Pageable.class), anyString()))
                .willReturn(responsePage);

        // when & then
        mockMvc.perform(get("/api/admin/orders")
                        .param("page", "0")
                        .param("size", "10")
                        .param("status", "PENDING")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // [4] JSON 경로 수정: $.content[0].orderId -> $.content[0].id
                .andExpect(jsonPath("$.data[0].id").value(1L))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("PUT /api/admin/orders/{orderId}/status - 상태 변경 성공")
    void updateOrderStatus() throws Exception {
        // given
        Long orderId = 1L;
        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
        ReflectionTestUtils.setField(request, "status", "DELIVERING");
        ReflectionTestUtils.setField(request, "trackingNumber", "12345678");

        // when & then
        mockMvc.perform(put("/api/admin/orders/{orderId}/status", orderId)
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(adminOrderService).updateOrderStatus(eq(orderId), any(OrderStatusUpdateRequest.class));
    }

    @Test
    @DisplayName("PUT /api/admin/orders/{orderId}/status - 요청 본문 누락시 400")
    void updateOrderStatus_BadRequest() throws Exception {
        // given
        Long orderId = 1L;

        // when & then
        mockMvc.perform(put("/api/admin/orders/{orderId}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)) // body 없음
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/admin/orders/returns/{returnId}/process - 반품 처리")
    void processReturn() throws Exception {
        // given
        Long returnId = 100L;
        boolean isApproved = true;

        // when & then
        mockMvc.perform(put("/api/admin/orders/returns/{returnId}/process", returnId)
                        .param("isApproved", String.valueOf(isApproved))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(adminOrderService).processReturn(returnId, isApproved);
    }
}