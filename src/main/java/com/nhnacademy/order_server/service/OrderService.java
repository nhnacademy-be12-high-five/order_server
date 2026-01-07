package com.nhnacademy.order_server.service;

import com.nhnacademy.order_server.dto.message.PaymentSuccessMessage;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.response.GuestOrderDetailResponse;
import com.nhnacademy.order_server.dto.response.OrderAggregationDto;
import com.nhnacademy.order_server.dto.response.OrderCreateResponse;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import com.nhnacademy.order_server.dto.response.OrderValidationInfoResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    OrderCreateResponse createOrder(OrderCreateRequest request);

    OrderValidationInfoResponse getValidationInfo(String orderKey);

    Page<OrderResponse> getMyOrders(Long userId, Pageable pageable);

    Page<OrderResponse> getMyOrdersLast3Months(Long userId, Pageable pageable);

    OrderResponse getOrderDetail(Long orderId);

    GuestOrderDetailResponse getGuestOrder(Long orderId, String password);

    List<OrderAggregationDto> getOrderAggregations(LocalDateTime start, LocalDateTime end);

    Long getTotalPaymentAmount(Long userId, LocalDateTime since);

    boolean hasPurchasedBook(Long memberId, Long bookId);

    void processPaymentSuccessMessage(PaymentSuccessMessage message);

    void purchaseConfirm(Long orderId);

    void cancelOrder(Long orderId);

    void autoCompleteDelivery();

    void autoConfirmPurchase();

    void cancelExpiredOrders();

    Map<Long, Long> getBulkTotalAmounts(List<Long> userIds, LocalDateTime since);
}