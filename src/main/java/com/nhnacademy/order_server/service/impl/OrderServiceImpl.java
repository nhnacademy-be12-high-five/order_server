package com.nhnacademy.order_server.service.impl;

import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.request.OrderReturnRequest;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import com.nhnacademy.order_server.dto.response.OrderReturnCheckResponse;
import com.nhnacademy.order_server.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    @Override
    @Transactional // 쓰기 작업이 있는 곳은 따로 붙여줌
    public Long createOrder(OrderCreateRequest request) {
        // TODO: 실제 주문 저장 로직 구현
        return 1L; // 임시 리턴
    }

    @Override
    public Page<OrderResponse> getMyOrders(Long userId, Pageable pageable) {
        // TODO: Repository 조회
        return Page.empty();
    }

    @Override
    public OrderResponse getOrderDetail(Long orderId) {
        return OrderResponse.builder().build(); // 임시
    }

    @Override
    public OrderResponse getGuestOrder(Long orderId, Integer password) {
        // TODO: 비밀번호 검증 로직
        return OrderResponse.builder().build();
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId) {
        // TODO: 상태 변경 로직 (WAITING -> CANCELED)
    }

    @Override
    public OrderReturnCheckResponse checkReturn(Long orderId) {
        // TODO: 날짜 계산 로직
        return OrderReturnCheckResponse.builder()
                .isEligible(true)
                .build();
    }

    @Override
    @Transactional
    public void requestReturn(Long orderId, OrderReturnRequest request) {
        // TODO: 반품 처리 로직
    }
}
