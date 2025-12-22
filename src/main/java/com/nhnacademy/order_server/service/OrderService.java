package com.nhnacademy.order_server.service;


import com.nhnacademy.order_server.dto.message.PaymentSuccessMessage;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.request.OrderReturnRequest;
import com.nhnacademy.order_server.dto.response.OrderCreateResponse;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import com.nhnacademy.order_server.dto.response.OrderReturnCheckResponse;
import com.nhnacademy.order_server.dto.response.OrderValidationInfoResponse;
import com.nhnacademy.order_server.service.impl.OrderServiceImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    OrderValidationInfoResponse getValidationInfo(String orderKey);
    OrderCreateResponse createOrder(OrderCreateRequest request);
    Page<OrderResponse> getMyOrders(Long userId, Pageable pageable);
    OrderResponse getOrderDetail(Long orderId);
    OrderResponse getGuestOrder(Long orderId, Integer password);
    void cancelOrder(Long orderId);
    void processPaymentSuccessMessage(PaymentSuccessMessage message);
    void cancelExpiredOrders();
    OrderCreateResponse createOrderTransactional(
            OrderCreateRequest request,
            Long userId,
            int usedPoint,
            String orderKey,
            double earnRate,
            OrderServiceImpl.OrderCalculationData orderData,
            OrderCreateRequest.OrderCalculationResult calculationResult
    );
}
