package com.nhnacademy.order_server.service;

import com.nhnacademy.order_server.dto.request.OrderStatusUpdateRequest;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminOrderService {

    Page<OrderResponse> getOrders(Pageable pageable, String status);
    void updateOrderStatus(Long orderId, OrderStatusUpdateRequest request);
    void completeOldDeliveries();
    void processReturn(Long returnId, boolean isApproved);
}