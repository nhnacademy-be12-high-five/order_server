package com.nhnacademy.order_server.service.impl;

import com.nhnacademy.order_server.dto.request.DeliveryPolicyRequest;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import com.nhnacademy.order_server.service.AdminOrderService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.print.Pageable;


@Service
public class AdminOrderServiceImpl implements AdminOrderService {

    @Override
    public Page<OrderResponse> getOrders(Pageable pageable, String status) {
        // TODO: QueryDSL 등을 이용한 동적 쿼리 구현 필요
        return Page.empty();
    }

    @Override
    @Transactional
    public void updateOrderStatus(Long orderId, String status) {
        // TODO: 주문 조회 후 상태 변경 (DeliveryStatus Enum 활용)
    }

    @Override
    @Transactional
    public void processReturn(Long returnId, boolean isApproved) {
        // TODO: 반품 승인 시 환불 로직 / 거절 시 상태 원복 로직
    }

    @Override
    @Transactional
    public void createDeliveryPolicy(DeliveryPolicyRequest request) {
        // TODO: 기존 정책 비활성화 후 새 정책 save
    }

    @Override
    @Transactional
    public void deleteDeliveryPolicy(Long policyId) {
        // TODO: 정책 삭제 (Soft Delete 권장)
    }
}
