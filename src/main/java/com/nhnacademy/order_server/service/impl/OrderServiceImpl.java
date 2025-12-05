package com.nhnacademy.order_server.service.impl;

import com.nhnacademy.order_server.adapter.BookClient;
import com.nhnacademy.order_server.adapter.CartClient;
import com.nhnacademy.order_server.adapter.CouponClient;
import com.nhnacademy.order_server.adapter.MemberClient;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.request.OrderReturnRequest;
import com.nhnacademy.order_server.dto.response.OrderCreateResponse;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import com.nhnacademy.order_server.dto.response.OrderReturnCheckResponse;
import com.nhnacademy.order_server.dto.response.OrderValidationInfoResponse;
import com.nhnacademy.order_server.dto.response.external.BookInfoResponse;
import com.nhnacademy.order_server.dto.response.external.MemberGradeResponse;
import com.nhnacademy.order_server.entity.*;
import com.nhnacademy.order_server.entity.enums.DeliveryStatus;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import com.nhnacademy.order_server.repository.DeliveryRepository;
import com.nhnacademy.order_server.repository.OrderRepository;
import com.nhnacademy.order_server.repository.WrapperRepository;
import com.nhnacademy.order_server.service.DeliveryService;
import com.nhnacademy.order_server.service.OrderService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final DeliveryRepository deliveryRepository;
    private final WrapperRepository wrapperRepository;
    private final DeliveryService deliveryService;

    private final BookClient bookClient;
    private final CouponClient couponClient;
    private final MemberClient memberClient;
    private final CartClient cartClient;

    @Override
    @Transactional
    public OrderCreateResponse createOrder(OrderCreateRequest request) {

        // 1. [검증] 비회원 검증
        if (request.getUserId() == null && request.getOrderPassword() == null) {
            throw new OrderException(OrderErrorCode.ORDER_PASSWORD_REQUIRED);
        }

        Long userId = request.getUserId();

        // 2. [MSA] 회원 등급별 적립률 조회
        double earnRate = getMemberEarnRate(userId);

        // 3. [계산] 상품 검증 및 가차감, 비용 계산
        OrderCalculationData orderData = calculateAndValidateOrderItems(request, earnRate);

        // 4. 배송비 계산
        int deliveryFee = deliveryService.calculateDeliveryFee(orderData.totalProductAmount, request.getReceiverAddress());

        // 5. [계산] 최종 할인 및 금액 계산 (여기에 수정사항 포함됨)
        OrderCreateRequest.OrderCalculationResult calculationResult = calculateFinalAmounts(
                request, orderData.totalProductAmount, orderData.totalWrappingFee, deliveryFee, orderData.totalEarnedPoint, userId);

        // 6. [UUID 생성] 주문 그룹 식별 키 생성
        String orderKey = UUID.randomUUID().toString();

        // 7. 엔티티 생성 및 저장
        Order order = request.toEntity(calculationResult, orderKey);

        for (OrderItem item : orderData.tempOrderItems) {
            order.addOrderItem(item);
        }
        orderRepository.save(order);

        // 8. 배송 정보 저장
        LocalDate requestDate = (request.getRequestDeliveryDate() != null) ?
                request.getRequestDeliveryDate() : LocalDate.now().plusDays(2);
        Delivery delivery = Delivery.builder()
                .order(order)
                .requestDeliveryDate(requestDate)
                .estimatedDeliveryDate(requestDate)
                .build();
        deliveryRepository.save(delivery);

        // 9. [후처리] 장바구니 비우기
        if (userId != null) {
            try {
                cartClient.clearCartByUserId(userId);
            } catch (Exception e) {
                log.warn("장바구니 비우기 요청 실패 (결과에 영향 없음): User={}", userId, e);
            }
        }

        String firstBookTitle = "주문 상품";
        // TODO: BookInfoResponse에서 실제 타이틀 가져오기

        return OrderCreateResponse.from(order, firstBookTitle, request.getOrderItems().size());
    }

    // --- 헬퍼 메서드 ---

    @Getter
    private static class OrderCalculationData {
        final List<OrderItem> tempOrderItems;
        final int totalProductAmount;
        final int totalWrappingFee;
        final int totalEarnedPoint;

        public OrderCalculationData(List<OrderItem> tempOrderItems, int totalProductAmount, int totalWrappingFee, int totalEarnedPoint) {
            this.tempOrderItems = tempOrderItems;
            this.totalProductAmount = totalProductAmount;
            this.totalWrappingFee = totalWrappingFee;
            this.totalEarnedPoint = totalEarnedPoint;
        }
    }

    private double getMemberEarnRate(Long userId) {
        if (userId == null) return 0.0;
        try {
            MemberGradeResponse gradeInfo = memberClient.getMemberGrade(userId);
            return gradeInfo.getEarnRate();
        } catch (Exception e) {
            log.error("회원 등급 조회 실패 (UserId: {}): {}", userId, e.getMessage());
            throw new OrderException(OrderErrorCode.MEMBER_SERVICE_ERROR);
        }
    }

    private OrderCalculationData calculateAndValidateOrderItems(OrderCreateRequest request, double earnRate) {
        Set<Long> wrapperIds = request.getOrderItems().stream()
                .map(OrderCreateRequest.OrderItemRequest::getWrapperId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Wrapper> wrapperMap = wrapperRepository.findAllById(wrapperIds).stream()
                .collect(Collectors.toMap(Wrapper::getId, wrapper -> wrapper));

        List<OrderItem> tempOrderItems = new ArrayList<>();
        int totalProductAmount = 0;
        int totalWrappingFee = 0;
        int totalEarnedPoint = 0;

        for (OrderCreateRequest.OrderItemRequest itemReq : request.getOrderItems()) {
            BookInfoResponse bookInfo;
            try {
                bookInfo = bookClient.getBookInfo(itemReq.getBookId());
            } catch (Exception e) {
                throw new OrderException(OrderErrorCode.EXTERNAL_SERVICE_ERROR);
            }
            int bookPrice = bookInfo.getPrice();

            try {
                bookClient.holdStock(itemReq.getBookId(), itemReq.getQuantity());
            } catch (Exception e) {
                throw new OrderException(OrderErrorCode.OUT_OF_STOCK);
            }

            totalEarnedPoint += (int) (bookPrice * itemReq.getQuantity() * earnRate);

            Wrapper wrapper = null;
            if (itemReq.getWrapperId() != null) {
                wrapper = wrapperMap.get(itemReq.getWrapperId());
                if (wrapper == null) throw new OrderException(OrderErrorCode.WRAPPER_NOT_FOUND);
                totalWrappingFee += wrapper.getWrapperPrice() * itemReq.getQuantity();
            }

            totalProductAmount += bookPrice * itemReq.getQuantity();
            tempOrderItems.add(itemReq.toEntity(bookPrice, wrapper));
        }

        return new OrderCalculationData(tempOrderItems, totalProductAmount, totalWrappingFee, totalEarnedPoint);
    }

    private OrderCreateRequest.OrderCalculationResult calculateFinalAmounts(
            OrderCreateRequest request, int totalProductAmount, int totalWrappingFee, int deliveryFee, int totalEarnedPoint, Long userId) {

        int couponDiscount = 0;
        if (request.getCouponId() != null) {
            try {
                couponDiscount = couponClient.calculateDiscount(request.getCouponId(), totalProductAmount);
            } catch (Exception e) {
                throw new OrderException(OrderErrorCode.COUPON_SERVICE_ERROR);
            }
            if (couponDiscount > totalProductAmount) couponDiscount = totalProductAmount;
        }

        int usedPoint = (request.getUsedPoint() != null) ? request.getUsedPoint() : 0;
        if (usedPoint > 0 && userId != null) {
            Integer balance;
            try {
                // [수정] 외부 통신만 try-catch로 감쌈
                balance = memberClient.getPointBalance(userId);
            } catch (Exception e) {
                throw new OrderException(OrderErrorCode.MEMBER_SERVICE_ERROR);
            }

            // [수정] 비즈니스 검증 로직은 밖으로 뺌 -> 이제 예외가 가려지지 않음
            if (balance < usedPoint) {
                throw new OrderException(OrderErrorCode.NOT_ENOUGH_POINT);
            }
        }

        int finalPaymentAmount = (totalProductAmount + totalWrappingFee + deliveryFee) - couponDiscount - usedPoint;
        if (finalPaymentAmount < 0) finalPaymentAmount = 0;

        return OrderCreateRequest.OrderCalculationResult.builder()
                .productAmount(totalProductAmount)
                .deliveryFee(deliveryFee)
                .wrappingFee(totalWrappingFee)
                .couponDiscount(couponDiscount)
                .pointDiscount(usedPoint)
                .paymentAmount(finalPaymentAmount)
                .earnedPoint(totalEarnedPoint)
                .build();
    }

    @Override
    @Transactional
    public void paymentSuccess(Long orderId, String paymentKey) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        if (order.getDeliveryStatus() != DeliveryStatus.PENDING) {
            throw new OrderException(OrderErrorCode.ALREADY_PROCESSED);
        }

        order.updateStatus(DeliveryStatus.WAITING);
        order.setPaymentKey(paymentKey);

        try {
            List<Long> bookIds = order.getOrderItems().stream().map(OrderItem::getBookId).collect(Collectors.toList());
            bookClient.confirmStockDeduction(bookIds);

            if (order.getCouponDiscount() != null && order.getCouponDiscount() > 0) {
                couponClient.useCoupon(order.getCouponId());
            }
            if (order.getPointDiscount() != null && order.getPointDiscount() > 0) {
                memberClient.deductPoint(order.getUserId(), order.getPointDiscount());
            }
        } catch (Exception e) {
            log.error("주문 확정 후처리 실패: OrderID={}, Error={}", orderId, e.getMessage());
        }
    }

    // ... 나머지 조회 메서드 (getMyOrders 등) ...
    @Override public Page<OrderResponse> getMyOrders(Long userId, Pageable pageable) { return Page.empty(); }
    @Override public OrderResponse getOrderDetail(Long orderId) { return OrderResponse.builder().build(); }
    @Override public OrderResponse getGuestOrder(Long orderId, Integer password) { return OrderResponse.builder().build(); }
    @Override public void cancelOrder(Long orderId) {}
    @Override public OrderReturnCheckResponse checkReturn(Long orderId) { return OrderReturnCheckResponse.builder().isEligible(true).build(); }
    @Override public void requestReturn(Long orderId, OrderReturnRequest request) {}
    @Override public OrderValidationInfoResponse getValidationInfo(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));
        return OrderValidationInfoResponse.from(order);
    }
}