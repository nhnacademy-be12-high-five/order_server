package com.nhnacademy.order_server.service;


import com.nhnacademy.order_server.dto.OrderCalculationData;
import com.nhnacademy.order_server.dto.message.PaymentSuccessMessage;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.response.GuestOrderDetailResponse;
import com.nhnacademy.order_server.dto.response.OrderCreateResponse;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import com.nhnacademy.order_server.dto.response.OrderValidationInfoResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {
    
    OrderValidationInfoResponse getValidationInfo(String orderKey);
    OrderCreateResponse createOrder(OrderCreateRequest request);
    Page<OrderResponse> getMyOrders(Long userId, Pageable pageable);
    OrderResponse getOrderDetail(Long orderId);
    GuestOrderDetailResponse getGuestOrder(Long orderId, String password);
    void cancelOrder(Long orderId);
    void processPaymentSuccessMessage(PaymentSuccessMessage message);
    void cancelExpiredOrders();
    Page<OrderResponse> getMyOrdersLast3Months(Long userId, Pageable pageable);
    OrderCreateResponse createOrderTransactional(
            OrderCreateRequest request,
            Long userId,
            int usedPoint,
            String orderKey,
            OrderCalculationData orderData,
            OrderCreateRequest.OrderCalculationResult calculationResult
    );

    void cancelOrderTransactional(Long orderId);
    void purchaseConfirm(Long orderId);
    boolean hasPurchasedBook(Long memberId, Long bookId);
}
