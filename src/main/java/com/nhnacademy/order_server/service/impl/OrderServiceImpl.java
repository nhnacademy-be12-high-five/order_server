package com.nhnacademy.order_server.service.impl;

import com.nhnacademy.order_server.adapter.*;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.request.PaymentCancelRequest;
import com.nhnacademy.order_server.dto.request.PaymentConfirmRequest;
import com.nhnacademy.order_server.dto.response.OrderCreateResponse;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import com.nhnacademy.order_server.dto.response.OrderValidationInfoResponse;
import com.nhnacademy.order_server.dto.response.external.BookInfoResponse;
import com.nhnacademy.order_server.dto.response.external.MemberGradeResponse;
import com.nhnacademy.order_server.entity.Delivery;
import com.nhnacademy.order_server.entity.Order;
import com.nhnacademy.order_server.entity.OrderItem;
import com.nhnacademy.order_server.entity.Wrapper;
import com.nhnacademy.order_server.entity.enums.DeliveryStatus;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import com.nhnacademy.order_server.repository.DeliveryRepository;
import com.nhnacademy.order_server.repository.OrderRepository;
import com.nhnacademy.order_server.repository.WrapperRepository;
import com.nhnacademy.order_server.service.DeliveryService;
import com.nhnacademy.order_server.service.OrderService;
import lombok.Builder;
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
import java.util.function.Function;
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
    private final PaymentClient paymentClient;

    private final PasswordEncoder passwordEncoder;

    private static final int DEFAULT_DELIVERY_DAYS = 2;

    @Override
    @Transactional
    public OrderCreateResponse createOrder(OrderCreateRequest request) {
        String encryptedPassword = validateAndEncryptPassword(request);
        Long userId = request.getUserId();
        int usedPoint = (request.getUsedPoint() != null) ? request.getUsedPoint() : 0;
        String orderKey = UUID.randomUUID().toString();

        reservePointsIfMember(userId, usedPoint);

        List<Long> heldStockBookIds = new ArrayList<>();

        try {
            double earnRate = getMemberEarnRate(userId);
            OrderCalculationData orderData = processOrderItemsAndHoldStock(request, earnRate, orderKey);

            heldStockBookIds.addAll(
                    orderData.getTempOrderItems().stream()
                            .map(OrderItem::getBookId)
                            .toList()
            );

            int deliveryFee = calculateDeliveryFee(orderData.getTotalProductAmount(), request.getReceiverAddress());
            OrderCreateRequest.OrderCalculationResult calculationResult = calculateFinalAmounts(
                    request, orderData, deliveryFee);

            Order order = saveOrder(request, calculationResult, orderKey, encryptedPassword, orderData.getTempOrderItems());
            saveDelivery(order, request.getRequestDeliveryDate());

            clearCartSilently(userId);

            return createOrderResponse(order, orderData.getFirstBookTitle(), request.getOrderItems().size());

        } catch (Exception e) {
            compensateTransaction(userId, usedPoint, heldStockBookIds, e);
            throw e;
        }
    }

    @Override
    @Transactional
    public void paymentSuccess(Long orderId, String paymentKey) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        validateOrderStatus(order, DeliveryStatus.PENDING);

        confirmPaymentWithPg(order, paymentKey);

        order.updateStatus(DeliveryStatus.WAITING);
        order.setPaymentKey(paymentKey);

        finalizeExternalResources(order);
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!isCancelable(order.getDeliveryStatus())) {
            throw new OrderException(OrderErrorCode.CANNOT_CANCEL_ORDER);
        }

        if (order.getDeliveryStatus() == DeliveryStatus.WAITING) {
            processWaitingOrderCancellation(order);
        } else if (order.getDeliveryStatus() == DeliveryStatus.PENDING) {
            processPendingOrderCancellation(order);
        }

        order.updateStatus(DeliveryStatus.CANCELED);
    }

    private String validateAndEncryptPassword(OrderCreateRequest request) {
        if (request.getUserId() == null) {
            if (request.getOrderPassword() == null || request.getOrderPassword().isBlank()) {
                throw new OrderException(OrderErrorCode.ORDER_PASSWORD_REQUIRED);
            }
            return passwordEncoder.encode(request.getOrderPassword());
        }
        return null;
    }

    private void reservePointsIfMember(Long userId, int usedPoint) {
        if (userId != null && usedPoint > 0) {
            try {
                memberClient.reservePoint(userId, usedPoint);
            } catch (Exception e) {
                log.error("포인트 예약 실패 UserID={}: {}", userId, e.getMessage());
                throw new OrderException(OrderErrorCode.NOT_ENOUGH_POINT);
            }
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

    private OrderCalculationData processOrderItemsAndHoldStock(OrderCreateRequest request, double earnRate, String orderKey) {
        Map<Long, Wrapper> wrapperMap = getWrapperMap(request.getOrderItems());
        Map<Long, BookInfoResponse> bookInfoMap = getBookInfoMap(request.getOrderItems());

        List<OrderItem> tempOrderItems = new ArrayList<>();
        int totalProductAmount = 0;
        int totalWrappingFee = 0;
        int totalEarnedPoint = 0;
        String firstBookTitle = null;

        for (int i = 0; i < request.getOrderItems().size(); i++) {
            OrderCreateRequest.OrderItemRequest itemReq = request.getOrderItems().get(i);
            BookInfoResponse bookInfo = bookInfoMap.get(itemReq.getBookId());

            if (bookInfo == null) throw new OrderException(OrderErrorCode.INVALID_REQUEST);
            if (i == 0) firstBookTitle = bookInfo.getTitle();

            try {
                String idempotencyKey = orderKey + "-" + itemReq.getBookId();
                bookClient.holdStock(itemReq.getBookId(), itemReq.getQuantity(), idempotencyKey);
            } catch (Exception e) {
                throw new OrderException(OrderErrorCode.OUT_OF_STOCK);
            }

            int itemAmount = bookInfo.getPrice() * itemReq.getQuantity();
            int itemEarnedPoint = (int) (itemAmount * earnRate);

            totalProductAmount += itemAmount;
            totalEarnedPoint += itemEarnedPoint;

            Wrapper wrapper = null;
            if (itemReq.getWrapperId() != null) {
                wrapper = wrapperMap.get(itemReq.getWrapperId());
                if (wrapper == null) throw new OrderException(OrderErrorCode.WRAPPER_NOT_FOUND);
                totalWrappingFee += wrapper.getWrapperPrice() * itemReq.getQuantity();
            }

            tempOrderItems.add(itemReq.toEntity(bookInfo.getPrice(), bookInfo.getTitle(), wrapper));
        }

        return OrderCalculationData.builder()
                .tempOrderItems(tempOrderItems)
                .totalProductAmount(totalProductAmount)
                .totalWrappingFee(totalWrappingFee)
                .totalEarnedPoint(totalEarnedPoint)
                .firstBookTitle(firstBookTitle)
                .build();
    }

    private Map<Long, Wrapper> getWrapperMap(List<OrderCreateRequest.OrderItemRequest> items) {
        Set<Long> wrapperIds = items.stream()
                .map(OrderCreateRequest.OrderItemRequest::getWrapperId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (wrapperIds.isEmpty()) return Collections.emptyMap();
        return wrapperRepository.findAllById(wrapperIds).stream()
                .collect(Collectors.toMap(Wrapper::getId, Function.identity()));
    }

    private Map<Long, BookInfoResponse> getBookInfoMap(List<OrderCreateRequest.OrderItemRequest> items) {
        List<Long> bookIds = items.stream()
                .map(OrderCreateRequest.OrderItemRequest::getBookId)
                .collect(Collectors.toList());
        try {
            return bookClient.getBookInfoBatch(bookIds).stream()
                    .collect(Collectors.toMap(BookInfoResponse::getBookId, Function.identity()));
        } catch (Exception e) {
            log.error("도서 정보 배치 조회 실패", e);
            throw new OrderException(OrderErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    private int calculateDeliveryFee(int productAmount, String address) {
        try {
            return deliveryService.calculateDeliveryFee(productAmount, address);
        } catch (Exception e) {
            log.error("배송비 계산 실패: {}", e.getMessage());
            throw new OrderException(OrderErrorCode.DELIVERY_FEE_CALCULATION_ERROR);
        }
    }

    private OrderCreateRequest.OrderCalculationResult calculateFinalAmounts(
            OrderCreateRequest request, OrderCalculationData data, int deliveryFee) {

        int couponDiscount = 0;
        if (request.getCouponId() != null) {
            try {
                couponDiscount = couponClient.calculateDiscount(request.getCouponId(), data.getTotalProductAmount());
            } catch (Exception e) {
                throw new OrderException(OrderErrorCode.COUPON_SERVICE_ERROR);
            }
            couponDiscount = Math.min(couponDiscount, data.getTotalProductAmount());
        }

        int usedPoint = (request.getUsedPoint() != null) ? request.getUsedPoint() : 0;
        int finalPaymentAmount = Math.max(0, (data.getTotalProductAmount() + data.getTotalWrappingFee() + deliveryFee) - couponDiscount - usedPoint);

        return OrderCreateRequest.OrderCalculationResult.builder()
                .productAmount(data.getTotalProductAmount())
                .deliveryFee(deliveryFee)
                .wrappingFee(data.getTotalWrappingFee())
                .couponDiscount(couponDiscount)
                .pointDiscount(usedPoint)
                .paymentAmount(finalPaymentAmount)
                .earnedPoint(data.getTotalEarnedPoint())
                .build();
    }

    private Order saveOrder(OrderCreateRequest request, OrderCreateRequest.OrderCalculationResult result,
                            String orderKey, String encryptedPassword, List<OrderItem> items) {
        Order order = request.toEntity(result, orderKey, encryptedPassword);
        items.forEach(order::addOrderItem);
        return orderRepository.save(order);
    }

    private void saveDelivery(Order order, LocalDate requestDate) {
        LocalDate date = (requestDate != null) ? requestDate : LocalDate.now().plusDays(DEFAULT_DELIVERY_DAYS);
        Delivery delivery = Delivery.builder()
                .order(order)
                .requestDeliveryDate(date)
                .estimatedDeliveryDate(date)
                .build();
        deliveryRepository.save(delivery);
    }

    private void clearCartSilently(Long userId) {
        if (userId != null) {
            try {
                cartClient.clearCartByUserId(userId);
            } catch (Exception e) {
                log.warn("장바구니 비우기 요청 실패: User={}", userId, e);
            }
        }
    }

    private OrderCreateResponse createOrderResponse(Order order, String firstBookTitle, int totalItems) {

        return OrderCreateResponse.from(order, firstBookTitle, totalItems);
    }

    private void compensateTransaction(Long userId, int usedPoint, List<Long> heldStockBookIds, Exception originalException) {
        if (userId != null && usedPoint > 0) {
            try {
                memberClient.cancelPoint(userId, usedPoint);
            } catch (Exception e) {
                log.error("CRITICAL: 포인트 예약 취소 실패! UserID={}, Amount={}", userId, usedPoint, e);
            }
        }
        if (!heldStockBookIds.isEmpty()) {
            try {
                log.info("주문 생성 실패로 인한 재고 롤백 시도: {}", heldStockBookIds);
                bookClient.releaseHeldStock(heldStockBookIds);
            } catch (Exception e) {
                log.error("CRITICAL: 재고 롤백 실패. 수동 복구 필요. IDs={}", heldStockBookIds, e);
            }
        }
    }



    private void validateOrderStatus(Order order, DeliveryStatus expected) {
        if (order.getDeliveryStatus() != expected) {
            throw new OrderException(OrderErrorCode.ALREADY_PROCESSED);
        }
    }

    private void confirmPaymentWithPg(Order order, String paymentKey) {
        try {
            PaymentConfirmRequest confirmRequest = PaymentConfirmRequest.builder()
                    .paymentKey(paymentKey)
                    .orderId(order.getOrderKey())
                    .amount(order.getPaymentAmount())
                    .build();
            paymentClient.confirmPayment(confirmRequest);
        } catch (Exception e) {
            log.error("결제 승인 실패: orderId={}, reason={}", order.getId(), e.getMessage());
            throw new OrderException(OrderErrorCode.EXTERNAL_API_ERROR);
        }
    }

    private void finalizeExternalResources(Order order) {
        List<String> failedOperations = new ArrayList<>();

        // 1. 재고 확정 (독립 처리)
        try {
            List<Long> bookIds = order.getOrderItems().stream()
                    .map(OrderItem::getBookId)
                    .toList();
            bookClient.confirmStockDeduction(bookIds);
        } catch (Exception e) {
            log.error("재고 확정 실패: OrderID={}, Error={}", order.getId(), e.getMessage());
            failedOperations.add("STOCK");
        }

        // 2. 쿠폰 사용 (독립 처리)
        if (order.getCouponId() != null) {
            try {
                couponClient.useCoupon(order.getCouponId());
            } catch (Exception e) {
                log.error("쿠폰 사용 확정 실패: OrderID={}, Error={}", order.getId(), e.getMessage());
                failedOperations.add("COUPON");
            }
        }

        // 3. 포인트 확정 (독립 처리)
        if (order.getPointDiscount() != null && order.getPointDiscount() > 0) {
            try {
                memberClient.confirmPoint(order.getUserId(), order.getPointDiscount());
            } catch (Exception e) {
                log.error("포인트 확정 실패: OrderID={}, Error={}", order.getId(), e.getMessage());
                failedOperations.add("POINT");
            }
        }

        // 4. 실패 내역이 있다면 주문 엔티티에 저장 (추후 재처리 위함)
        if (!failedOperations.isEmpty()) {
            log.warn("주문 후처리 일부 실패. 재처리 필요 항목: {}", failedOperations);
            order.setPendingOperations(failedOperations);
            // 필요 시 주문 상태를 'PARTIAL_COMPLETED' 등으로 변경하거나 알림 전송 로직 추가 가능
        }
    }

    private boolean isCancelable(DeliveryStatus status) {
        return status == DeliveryStatus.WAITING || status == DeliveryStatus.PENDING;
    }

    private void processWaitingOrderCancellation(Order order) {
        // 1. PG사 결제 취소 (예외 처리 추가)
        if (order.getPaymentKey() != null) {
            try {
                PaymentCancelRequest cancelRequest = new PaymentCancelRequest("사용자 주문 취소", order.getPaymentAmount());
                paymentClient.cancelPayment(order.getPaymentKey(), cancelRequest);
            } catch (Exception e) {
                log.error("PG 결제 취소 실패: OrderID={}, Error={}", order.getId(), e.getMessage());
                // PG 취소 실패는 치명적이므로 예외를 던져 트랜잭션 롤백 유도 (또는 정책에 따라 처리)
                throw new OrderException(OrderErrorCode.PAYMENT_CANCEL_FAILED);
            }
        }

        // 2. 포인트 복구 (메서드 변경 및 예외 처리)
        if (order.getPointDiscount() != null && order.getPointDiscount() > 0) {
            try {
                // [수정] reservePoint -> cancelPoint (취소/환불) 사용
                memberClient.cancelPoint(order.getUserId(), order.getPointDiscount());
            } catch (Exception e) {
                log.error("포인트 취소 실패: OrderID={}, Error={}", order.getId(), e.getMessage());
                // 외부 서비스 오류 시 롤백
                throw new OrderException(OrderErrorCode.MEMBER_SERVICE_ERROR);
            }
        }

        // 3. 쿠폰 복구 (예외 처리 추가)
        if (order.getCouponId() != null) {
            try {
                couponClient.cancelCouponUsage(order.getCouponId());
            } catch (Exception e) {
                log.error("쿠폰 취소 실패: OrderID={}, Error={}", order.getId(), e.getMessage());
                throw new OrderException(OrderErrorCode.COUPON_SERVICE_ERROR);
            }
        }

        // 4. 재고 복구 (기존 로직 유지)
        try {
            List<Long> bookIds = order.getOrderItems().stream().map(OrderItem::getBookId).toList();
            String idempotencyKey = order.getId() + "-restore";
            bookClient.restoreStock(bookIds,idempotencyKey);
        } catch (Exception e) {
            log.error("재고 복구 실패 (WAITING 취소): OrderID={}", order.getId(), e);
            // 재고 복구 실패도 데이터 불일치를 유발하므로 예외를 던지는 것이 안전함
            throw new OrderException(OrderErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    private void processPendingOrderCancellation(Order order) {
        // 선점된 재고 해제
        try {
            List<Long> bookIds = order.getOrderItems().stream().map(OrderItem::getBookId).toList();
            bookClient.releaseHeldStock(bookIds);
        } catch (Exception e) {
            log.error("재고 선점 해제 실패 (PENDING 취소): OrderID={}", order.getId(), e);
        }
    }

    @Override
    public Page<OrderResponse> getMyOrders(Long userId, Pageable pageable) {
        return orderRepository.findAllByUserId(userId, pageable).map(OrderResponse::from);
    }

    @Override
    public OrderResponse getOrderDetail(Long orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));
        return OrderResponse.from(order);
    }

    @Override
    public OrderResponse getGuestOrder(Long orderId, Integer password) {
        Order order = orderRepository.findByIdAndOrderPassword(orderId, password)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));
        return OrderResponse.from(order);
    }

    @Override
    public OrderValidationInfoResponse getValidationInfo(String orderKey) {
        Order order = orderRepository.findByOrderKey(orderKey)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));
        return OrderValidationInfoResponse.from(order);
    }

    @Getter
    @Builder
    private static class OrderCalculationData {
        private final List<OrderItem> tempOrderItems;
        private final int totalProductAmount;
        private final int totalWrappingFee;
        private final int totalEarnedPoint;
        private final String firstBookTitle;
    }
}