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
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public OrderCreateResponse createOrder(OrderCreateRequest request) {

        // 1. [검증] 비회원 검증 및 비밀번호 암호화
        String encryptedPassword = null;
        if (request.getUserId() == null) {
            if (request.getOrderPassword() == null || request.getOrderPassword().isBlank()) {
                throw new OrderException(OrderErrorCode.ORDER_PASSWORD_REQUIRED);
            }
            // [보안] 비밀번호 해싱 저장
            encryptedPassword = passwordEncoder.encode(request.getOrderPassword());
        }

        Long userId = request.getUserId();
        int usedPoint = (request.getUsedPoint() != null) ? request.getUsedPoint() : 0;

        // 2. [MSA] 회원 등급별 적립률 조회 (장애 시 기본값 0.0 처리됨)
        double earnRate = getMemberEarnRate(userId);

        // 3. [동시성 제어] 포인트 선점 (Reservation Pattern)
        if (userId != null && usedPoint > 0) {
            try {
                memberClient.reservePoint(userId, usedPoint);
            } catch (Exception e) {
                log.error("포인트 예약 실패 UserID={}: {}", userId, e.getMessage());
                throw new OrderException(OrderErrorCode.NOT_ENOUGH_POINT);
            }
        }

        try {
            // 4. [Saga] 상품 검증 및 재고 가차감 (보상 트랜잭션 포함)
            OrderCalculationData orderData = calculateAndValidateOrderItems(request, earnRate);

            // 5. [예외처리] 배송비 계산
            int deliveryFee;
            try {
                deliveryFee = deliveryService.calculateDeliveryFee(orderData.totalProductAmount, request.getReceiverAddress());
            } catch (Exception e) {
                log.error("배송비 계산 실패: {}", e.getMessage());
                throw new OrderException(OrderErrorCode.DELIVERY_FEE_CALCULATION_ERROR);
            }

            // 6. [계산] 최종 할인 및 금액 계산 (단순 계산만 수행)
            OrderCreateRequest.OrderCalculationResult calculationResult = calculateFinalAmounts(
                    request, orderData.totalProductAmount, orderData.totalWrappingFee, deliveryFee, orderData.totalEarnedPoint);

            // 7. [UUID 생성]
            String orderKey = UUID.randomUUID().toString();

            // 8. 엔티티 생성 및 저장
            Order order = request.toEntity(calculationResult, orderKey, encryptedPassword);
            for (OrderItem item : orderData.tempOrderItems) {
                order.addOrderItem(item);
            }
            orderRepository.save(order);

            // 9. 배송 정보 저장
            LocalDate requestDate = (request.getRequestDeliveryDate() != null) ?
                    request.getRequestDeliveryDate() : LocalDate.now().plusDays(2);
            Delivery delivery = Delivery.builder()
                    .order(order)
                    .requestDeliveryDate(requestDate)
                    .estimatedDeliveryDate(requestDate)
                    .build();
            deliveryRepository.save(delivery);

            // 10. [후처리] 장바구니 비우기
            if (userId != null) {
                try {
                    cartClient.clearCartByUserId(userId);
                } catch (Exception e) {
                    log.warn("장바구니 비우기 요청 실패 (결과에 영향 없음): User={}", userId, e);
                }
            }

            String firstBookTitle = orderData.firstBookTitle != null ? orderData.firstBookTitle : "주문 상품";
            if (request.getOrderItems().size() > 1) {
                firstBookTitle += " 외 " + (request.getOrderItems().size() - 1) + "건";
            }

            return OrderCreateResponse.from(order, firstBookTitle, request.getOrderItems().size());

        } catch (Exception e) {
            // [보상 트랜잭션] 주문 생성 실패 시 예약된 포인트 취소
            if (userId != null && usedPoint > 0) {
                try {
                    log.info("주문 생성 실패로 인한 포인트 예약 취소: UserID={}, Amount={}", userId, usedPoint);
                    memberClient.cancelPoint(userId, usedPoint);
                } catch (Exception cancelEx) {
                    log.error("CRITICAL: 포인트 예약 취소 실패! 수동 복구 필요. UserID={}, Amount={}", userId, usedPoint, cancelEx);
                }
            }
            throw e; // 원래 예외 전파
        }
    }

    // --- 헬퍼 메서드 ---

    @Getter
    private static class OrderCalculationData {
        final List<OrderItem> tempOrderItems;
        final int totalProductAmount;
        final int totalWrappingFee;
        final int totalEarnedPoint;
        final String firstBookTitle;

        public OrderCalculationData(List<OrderItem> tempOrderItems, int totalProductAmount, int totalWrappingFee, int totalEarnedPoint, String firstBookTitle) {
            this.tempOrderItems = tempOrderItems;
            this.totalProductAmount = totalProductAmount;
            this.totalWrappingFee = totalWrappingFee;
            this.totalEarnedPoint = totalEarnedPoint;
            this.firstBookTitle = firstBookTitle;
        }
    }

    private double getMemberEarnRate(Long userId) {
        if (userId == null) return 0.0;
        try {
            MemberGradeResponse gradeInfo = memberClient.getMemberGrade(userId);
            return gradeInfo.getEarnRate();
        } catch (Exception e) {
            log.warn("회원 등급 조회 실패, 기본 적립률 적용 (userId={}): {}", userId, e.getMessage());
            return 0.0;
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
        List<Long> heldBookIds = new ArrayList<>(); // [Saga] 가차감 성공한 도서 ID 추적

        int totalProductAmount = 0;
        int totalWrappingFee = 0;
        int totalEarnedPoint = 0;
        String firstBookTitle = null;

        try {
            for (int i = 0; i < request.getOrderItems().size(); i++) {
                OrderCreateRequest.OrderItemRequest itemReq = request.getOrderItems().get(i);

                BookInfoResponse bookInfo;
                try {
                    bookInfo = bookClient.getBookInfo(itemReq.getBookId());
                } catch (Exception e) {
                    throw new OrderException(OrderErrorCode.EXTERNAL_SERVICE_ERROR);
                }

                if (i == 0) firstBookTitle = bookInfo.getTitle();
                int bookPrice = bookInfo.getPrice();

                // 1. 재고 가차감 시도
                try {
                    bookClient.holdStock(itemReq.getBookId(), itemReq.getQuantity());
                    heldBookIds.add(itemReq.getBookId()); // [Saga] 성공 목록에 추가
                } catch (Exception e) {
                    throw new OrderException(OrderErrorCode.OUT_OF_STOCK);
                }

                // 2. 금액 계산
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
        } catch (Exception e) {
            // [Saga] 예외 발생 시 가차감했던 재고 롤백
            if (!heldBookIds.isEmpty()) {
                log.info("주문 처리 중 예외 발생. 재고 롤백 시도: {}", heldBookIds);
                try {
                    bookClient.releaseHeldStock(heldBookIds);
                } catch (Exception releaseEx) {
                    log.error("CRITICAL: 재고 롤백 실패. 수동 복구 필요. IDs={}", heldBookIds, releaseEx);
                }
            }
            throw e;
        }

        return new OrderCalculationData(tempOrderItems, totalProductAmount, totalWrappingFee, totalEarnedPoint, firstBookTitle);
    }

    private OrderCreateRequest.OrderCalculationResult calculateFinalAmounts(
            OrderCreateRequest request, int totalProductAmount, int totalWrappingFee, int deliveryFee, int totalEarnedPoint) {

        int couponDiscount = 0;
        if (request.getCouponId() != null) {
            try {
                couponDiscount = couponClient.calculateDiscount(request.getCouponId(), totalProductAmount);
            } catch (Exception e) {
                throw new OrderException(OrderErrorCode.COUPON_SERVICE_ERROR);
            }
            if (couponDiscount > totalProductAmount) couponDiscount = totalProductAmount;
        }

        // [변경] 포인트 잔액 검증 제거 (이미 reservePoint에서 검증됨)
        int usedPoint = (request.getUsedPoint() != null) ? request.getUsedPoint() : 0;

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

            // [변경] 포인트 차감 확정 (confirm)
            if (order.getPointDiscount() != null && order.getPointDiscount() > 0) {
                memberClient.confirmPoint(order.getUserId(), order.getPointDiscount());
            }
        } catch (Exception e) {
            log.error("주문 확정 후처리 실패: OrderID={}, Error={}", orderId, e.getMessage());
            // TODO: 결제는 성공했으나 후처리가 실패한 경우, 별도 배치나 메시지 큐를 통해 재시도해야 합니다.
        }
    }

    // ... 기존 조회 메서드들 (생략) ...
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