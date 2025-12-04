package com.nhnacademy.order_server.service.impl;

import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.request.OrderReturnRequest;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import com.nhnacademy.order_server.dto.response.OrderReturnCheckResponse;
import com.nhnacademy.order_server.entity.*;
import com.nhnacademy.order_server.entity.enums.DeliveryStatus;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import com.nhnacademy.order_server.repository.DeliveryRepository;
import com.nhnacademy.order_server.repository.OrderRepository;
import com.nhnacademy.order_server.repository.WrapperRepository;
import com.nhnacademy.order_server.service.DeliveryService;
import com.nhnacademy.order_server.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final DeliveryRepository deliveryRepository;
    private final WrapperRepository wrapperRepository;
    private final DeliveryService deliveryService;

    private static final int FIXED_BOOK_PRICE = 15000;
    private static final int FIXED_COUPON_DISCOUNT = 3000;
    private static final double EARN_RATE = 0.05;

    @Override
    @Transactional
    public Long createOrder(OrderCreateRequest request) {
        // 1. 비회원 검증
        if (request.getUserId() == null && request.getOrderPassword() == null) {
            throw new OrderException(OrderErrorCode.ORDER_PASSWORD_REQUIRED);
        }

        List<OrderItem> tempOrderItems = new ArrayList<>();
        int totalProductAmount = 0;
        int totalWrappingFee = 0;
        int totalEarnedPoint = 0;

        // 2. 계산 로직 (상품, 포장, 적립금)
        for (OrderCreateRequest.OrderItemRequest itemReq : request.getOrderItems()) {
            int bookPrice = FIXED_BOOK_PRICE;

            // [Feign Mock] 재고 확인 (여기서 재고를 '가차감' 해야 함)

            totalEarnedPoint += (int) (bookPrice * itemReq.getQuantity() * EARN_RATE);

            Wrapper wrapper = null;
            if (itemReq.getWrapperId() != null) {
                wrapper = wrapperRepository.findById(itemReq.getWrapperId())
                        .orElseThrow(() -> new OrderException(OrderErrorCode.WRAPPER_NOT_FOUND));
                totalWrappingFee += wrapper.getWrapperPrice() * itemReq.getQuantity();
            }

            totalProductAmount += bookPrice * itemReq.getQuantity();
            tempOrderItems.add(itemReq.toEntity(bookPrice, wrapper));
        }

        // 3. 배송비 및 할인 계산
        int deliveryFee = deliveryService.calculateDeliveryFee(totalProductAmount, request.getReceiverAddress());

        int couponDiscount = 0;
        if (request.getCouponId() != null) {
            couponDiscount = FIXED_COUPON_DISCOUNT;
            if (couponDiscount > totalProductAmount) couponDiscount = totalProductAmount;
        }

        int usedPoint = (request.getUsedPoint() != null) ? request.getUsedPoint() : 0;
        // [Feign Mock] 포인트 잔액 검증

        int finalPaymentAmount = (totalProductAmount + totalWrappingFee + deliveryFee) - couponDiscount - usedPoint;
        if (finalPaymentAmount < 0) finalPaymentAmount = 0;

        // -----------------------------------------------------------------
        // 4. [FIX 1] 주문 그룹 식별 키 (UUID) 생성
        String orderKey = UUID.randomUUID().toString();

        // 5. [FIX 2] 계산 결과를 DTO로 묶음
        OrderCreateRequest.OrderCalculationResult calculationResult =
                OrderCreateRequest.OrderCalculationResult.builder()
                        .productAmount(totalProductAmount)
                        .deliveryFee(deliveryFee)
                        .wrappingFee(totalWrappingFee)
                        .couponDiscount(couponDiscount)
                        .pointDiscount(usedPoint)
                        .paymentAmount(finalPaymentAmount)
                        .earnedPoint(totalEarnedPoint)
                        .build();

        // 6. [FIX 3] 엔티티 생성 (orderKey를 파라미터로 전달)
        Order order = request.toEntity(calculationResult, orderKey);

        // 7. 저장 로직
        for (OrderItem item : tempOrderItems) {
            order.addOrderItem(item);
        }
        orderRepository.save(order);

        // 배송 정보 저장
        LocalDate requestDate = (request.getRequestDeliveryDate() != null) ?
                request.getRequestDeliveryDate() : LocalDate.now().plusDays(2);

        Delivery delivery = Delivery.builder()
                .order(order)
                .requestDeliveryDate(requestDate)
                .estimatedDeliveryDate(requestDate)
                .build();
        deliveryRepository.save(delivery);

        return order.getId();
    }

    /**
     * [신규] 결제 성공 처리 메서드
     * 결제 서비스(Payment Server)에서 결제가 완료되면 이 API를 호출함
     */
    @Transactional
    public void paymentSuccess(Long orderId) {
        // 1. 주문 조회
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        // 2. 상태 검증 (이미 처리된 주문인지)
        if (order.getDeliveryStatus() != DeliveryStatus.PENDING) {
            throw new OrderException(OrderErrorCode.ALREADY_PROCESSED); // 에러코드 추가 필요
        }

        // 3. 상태 변경 (PENDING -> WAITING)
        // Order 엔티티에 updateStatus 메서드 추가 필요
        order.updateStatus(DeliveryStatus.WAITING);

        // 4. [후처리] 쿠폰 사용 확정 & 포인트 차감 & 재고 확정
        // Mock 로깅
        log.info("주문(ID:{}) 결제 완료 -> 상태 변경(WAITING), 쿠폰/포인트/재고 확정 처리", orderId);

        // if (order.getCouponDiscount() > 0) couponClient.useCoupon(couponId);
        // if (order.getPointDiscount() > 0) memberClient.usePoint(userId, amount);
    }

    // ... 기존 조회 메서드들 ...
    @Override
    public Page<OrderResponse> getMyOrders(Long userId, Pageable pageable) { return Page.empty(); }
    @Override
    public OrderResponse getOrderDetail(Long orderId) { return OrderResponse.builder().build(); }
    @Override
    public OrderResponse getGuestOrder(Long orderId, Integer password) { return OrderResponse.builder().build(); }
    @Override
    public void cancelOrder(Long orderId) {}
    @Override
    public OrderReturnCheckResponse checkReturn(Long orderId) { return OrderReturnCheckResponse.builder().isEligible(true).build(); }
    @Override
    public void requestReturn(Long orderId, OrderReturnRequest request) {}
}