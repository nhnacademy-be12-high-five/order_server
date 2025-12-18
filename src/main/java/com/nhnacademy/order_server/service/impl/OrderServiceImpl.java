package com.nhnacademy.order_server.service.impl;

import com.nhnacademy.order_server.adapter.*;
import com.nhnacademy.order_server.dto.message.PaymentSuccessMessage;
import com.nhnacademy.order_server.dto.request.*;
import com.nhnacademy.order_server.dto.request.CouponCalculationRequest;
import com.nhnacademy.order_server.dto.request.MemberCouponUseRequest;
import com.nhnacademy.order_server.dto.request.MemberCouponCancelRequest;

import com.nhnacademy.order_server.dto.response.CouponCalculationResponse;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
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

        // 1. 재고 선점 (가장 먼저 수행)
        List<Long> heldStockBookIds = request.getOrderItems().stream()
                .map(OrderCreateRequest.OrderItemRequest::getBookId)
                .distinct()
                .toList();

        try {
            double earnRate = getMemberEarnRate(userId);

            // 재고 선점 및 계산 데이터 준비
            OrderCalculationData orderData = processOrderItemsAndHoldStock(request, earnRate, orderKey);

            // 2. 최종 금액 계산
            int deliveryFee = calculateDeliveryFee(orderData.totalProductAmount(), request.getReceiverAddress());
            OrderCreateRequest.OrderCalculationResult calculationResult = calculateFinalAmounts(
                    request, orderData, deliveryFee);

            // 3. 주문 저장 (이 시점에 Order ID 생성됨)
            Order order = saveOrder(request, calculationResult, orderKey, encryptedPassword, orderData.tempOrderItems());

            // 4. 포인트 예약 (생성된 orderId 사용) - [수정됨]
            reservePointsIfMember(userId, usedPoint, order.getId());

            // 5. 배송 정보 저장
            saveDelivery(order, request.getRequestDeliveryDate());

            // 6. 장바구니 비우기
            clearCartSilently(userId);

            return createOrderResponse(order, orderData.firstBookTitle(), request.getOrderItems().size());

        } catch (Exception e) {
            // 실패 시 보상 트랜잭션 (포인트 취소 & 재고 롤백)
            compensateTransaction(userId, usedPoint, heldStockBookIds, orderKey, e);
            throw e;
        }
    }

    @Override
    @Transactional
    public void processPaymentSuccessMessage(PaymentSuccessMessage message) {
        Long orderId = message.getOrderId();

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        if (order.getDeliveryStatus() != DeliveryStatus.PENDING) {
            log.info("이미 처리된 주문입니다. (Idempotency Check) OrderID={}, Status={}", orderId, order.getDeliveryStatus());
            return;
        }

        if (order.getPaymentAmount() != message.getTotalAmount().intValue()) {
            log.error("주문 금액 불일치! OrderID={}, OrderAmount={}, PaidAmount={}",
                    orderId, order.getPaymentAmount(), message.getTotalAmount());
            throw new OrderException(OrderErrorCode.INVALID_REQUEST);
        }

        order.updateStatus(DeliveryStatus.WAITING);
        order.setPaymentKey(message.getPaymentKey());

        finalizeExternalResources(order);

        log.info("RabbitMQ 결제 메시지 처리 완료. OrderID={}", orderId);
    }

    @Override
    @Transactional
    public void paymentSuccess(Long orderId, String paymentKey) {
        log.info("결제 승인 요청 시작: orderId={}, key={}", orderId, paymentKey);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        validateOrderStatus(order, DeliveryStatus.PENDING);

        try {
            confirmPaymentWithPg(order, paymentKey);
            log.info("PG 승인 성공");
        } catch (Exception e) {
            log.error("PG 승인 실패: {}", e.getMessage(), e);
            throw e;
        }

        order.updateStatus(DeliveryStatus.WAITING);
        order.setPaymentKey(paymentKey);

        try {
            finalizeExternalResources(order);
            log.info("외부 리소스 확정 성공");
        } catch (Exception e) {
            log.error("외부 리소스 확정 중 오류 발생 (롤백됨): {}", e.getMessage(), e);
            throw e;
        }
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

    // [수정] orderId 파라미터 추가
    private void reservePointsIfMember(Long userId, int usedPoint, Long orderId) {
        if (userId != null && usedPoint > 0) {
            try {
                // MemberClient 호출 시 orderId 전달
                memberClient.reservePoint(userId, usedPoint, orderId);
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

        // 1. 병합을 위한 맵 (Key: "책ID:포장지ID", Value: 누적 수량)
        Map<String, Integer> mergedQuantityMap = new LinkedHashMap<>();
        Map<String, Long> keyToBookIdMap = new HashMap<>();
        Map<String, Long> keyToWrapperIdMap = new HashMap<>();

        String firstBookTitle = null;
        int totalProductAmount = 0;
        int totalWrappingFee = 0;
        int totalEarnedPoint = 0;

        // 2. 요청 아이템 순회하며 병합 및 금액 계산
        for (OrderCreateRequest.OrderItemRequest itemReq : request.getOrderItems()) {
            Long bookId = itemReq.getBookId();
            BookInfoResponse bookInfo = bookInfoMap.get(bookId);
            if (bookInfo == null) throw new OrderException(OrderErrorCode.INVALID_REQUEST);

            if (firstBookTitle == null) firstBookTitle = bookInfo.getTitle();

            Long wrapperId = itemReq.getWrapperId();
            String key = bookId + ":" + (wrapperId != null ? wrapperId : "null");

            mergedQuantityMap.merge(key, itemReq.getQuantity(), Integer::sum);
            keyToBookIdMap.put(key, bookId);
            keyToWrapperIdMap.put(key, wrapperId);

            int itemAmount = bookInfo.getPrice() * itemReq.getQuantity();
            totalProductAmount += itemAmount;
            totalEarnedPoint += (int) (itemAmount * earnRate);

            if (wrapperId != null) {
                Wrapper wrapper = wrapperMap.get(wrapperId);
                if (wrapper == null) throw new OrderException(OrderErrorCode.WRAPPER_NOT_FOUND);
                totalWrappingFee += wrapper.getWrapperPrice() * itemReq.getQuantity();
            }
        }

        // 3. 병합된 결과로 OrderItem 엔티티 및 재고 요청 생성
        List<OrderItem> finalOrderItems = new ArrayList<>();
        Map<Long, Integer> stockMap = new HashMap<>();

        for (Map.Entry<String, Integer> entry : mergedQuantityMap.entrySet()) {
            String key = entry.getKey();
            Integer totalQty = entry.getValue();
            Long bookId = keyToBookIdMap.get(key);
            Long wrapperId = keyToWrapperIdMap.get(key);

            BookInfoResponse bookInfo = bookInfoMap.get(bookId);
            Wrapper wrapper = (wrapperId != null) ? wrapperMap.get(wrapperId) : null;

            stockMap.merge(bookId, totalQty, Integer::sum);

            OrderItem orderItem = OrderItem.builder()
                    .bookId(bookId)
                    .bookTitle(bookInfo.getTitle())
                    .quantity(totalQty)
                    .unitPrice(bookInfo.getPrice())
                    .wrapper(wrapper)
                    .isWrapped(wrapper != null)
                    .key(UUID.randomUUID().toString())
                    .build();

            finalOrderItems.add(orderItem);
        }

        // 4. 재고 선점 요청 (Batch)
        List<StockRequest> stockRequests = stockMap.entrySet().stream()
                .map(e -> new StockRequest(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        try {
            if (!stockRequests.isEmpty()) {
                bookClient.holdStockBatch(stockRequests, orderKey);
                log.info("Batch Stock hold success: OrderKey={}, UniqueItems={}", orderKey, stockRequests.size());
            }
        } catch (Exception e) {
            log.error("Batch Stock hold failed: OrderKey={}", orderKey, e);
            throw new OrderException(OrderErrorCode.OUT_OF_STOCK);
        }

        return OrderCalculationData.builder()
                .tempOrderItems(finalOrderItems)
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
                .distinct()
                .collect(Collectors.toList());
        try {
            ResponseEntity<List<BookInfoResponse>> response = bookClient.getBooksBulk(bookIds);

            if (response == null || response.getBody() == null || response.getBody().isEmpty()) {
                log.error("도서 정보 조회 응답이 비어있음: bookIds={}", bookIds);
                return Collections.emptyMap();
            }

            return response.getBody().stream()
                    .collect(Collectors.toMap(
                            BookInfoResponse::getBookId,
                            Function.identity(),
                            (existing, replacement) -> existing
                    ));
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
                CouponCalculationRequest calcReq = new CouponCalculationRequest(
                        request.getCouponId(),
                        (long) data.totalProductAmount()
                );

                if (request.getUserId() != null) {
                    CouponCalculationResponse response = couponClient.calculateCoupon(request.getUserId(), calcReq);
                    if (response != null && response.getDiscountAmount() != null) {
                        couponDiscount = response.getDiscountAmount().intValue();
                    }
                }

            } catch (Exception e) {
                log.error("쿠폰 할인 계산 실패", e);
                throw new OrderException(OrderErrorCode.COUPON_SERVICE_ERROR);
            }
            couponDiscount = Math.min(couponDiscount, data.totalProductAmount());
        }

        int usedPoint = (request.getUsedPoint() != null) ? request.getUsedPoint() : 0;

        int finalPaymentAmount = Math.max(0, (data.totalProductAmount() + data.totalWrappingFee() + deliveryFee) - couponDiscount - usedPoint);

        return OrderCreateRequest.OrderCalculationResult.builder()
                .productAmount(data.totalProductAmount())
                .deliveryFee(deliveryFee)
                .wrappingFee(data.totalWrappingFee())
                .couponDiscount(couponDiscount)
                .pointDiscount(usedPoint)
                .paymentAmount(finalPaymentAmount)
                .earnedPoint(data.totalEarnedPoint())
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
                cartClient.clearCart(userId);
            } catch (Exception e) {
                log.warn("장바구니 비우기 요청 실패: User={}", userId, e);
            }
        }
    }

    private OrderCreateResponse createOrderResponse(Order order, String firstBookTitle, int totalItems) {
        return OrderCreateResponse.from(order, firstBookTitle, totalItems);
    }

    private void compensateTransaction(Long userId, int usedPoint, List<Long> heldStockBookIds, String orderKey, Exception originalException) {
        if (userId != null && usedPoint > 0) {
            try {
                // 포인트 예약 취소 (실패 시 orderId가 없을 수 있으므로 0L 전달)
                memberClient.cancelPoint(userId, usedPoint, 0L);
            } catch (Exception e) {
                log.error("CRITICAL: 포인트 예약 취소 실패! UserID={}, Amount={}", userId, usedPoint, e);
            }
        }
        if (!heldStockBookIds.isEmpty()) {
            try {
                log.info("주문 생성 실패로 인한 재고 롤백 시도: {}", heldStockBookIds);
                bookClient.releaseHeldStock(heldStockBookIds, orderKey);
            } catch (Exception e) {
                log.error("CRITICAL: 재고 롤백 실패. 수동 복구 필요. IDs={}, OrderKey={}", heldStockBookIds, orderKey, e);
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
                    .orderKey(order.getOrderKey())
                    .amount(order.getPaymentAmount())
                    .paymentMethod("Toss")
                    .build();
            paymentClient.confirmPayment(confirmRequest);
        } catch (Exception e) {
            log.error("결제 승인 실패: orderId={}, reason={}", order.getId(), e.getMessage());
            throw new OrderException(OrderErrorCode.EXTERNAL_API_ERROR);
        }
    }

    // [수정] orderId 파라미터 추가
    private void finalizeExternalResources(Order order) {
        List<String> failedOperations = new ArrayList<>();

        // 1. 재고 확정
        try {
            List<Long> bookIds = new ArrayList<>();
            for (OrderItem item : order.getOrderItems()) {
                for (int i = 0; i < item.getQuantity(); i++) {
                    bookIds.add(item.getBookId());
                }
            }
            bookClient.confirmStockDeduction(bookIds, order.getOrderKey());
        } catch (Exception e) {
            log.error("재고 확정 실패: OrderID={}, Error={}", order.getId(), e.getMessage());
            failedOperations.add("STOCK");
        }

        // 2. 쿠폰 사용 확정
        if (order.getCouponId() != null) {
            try {
                MemberCouponUseRequest useReq = new MemberCouponUseRequest(
                        order.getCouponId(),
                        order.getId()
                );
                couponClient.useCoupon(order.getUserId(), useReq);

            } catch (Exception e) {
                log.error("쿠폰 사용 확정 실패: OrderID={}, Error={}", order.getId(), e.getMessage());
                failedOperations.add("COUPON");
            }
        }

        // 3. 포인트 차감 확정 (orderId 추가)
        if (order.getPointDiscount() != null && order.getPointDiscount() > 0) {
            try {
                memberClient.confirmPoint(order.getUserId(), order.getPointDiscount(), order.getId());
            } catch (Exception e) {
                log.error("포인트 확정 실패: OrderID={}, Error={}", order.getId(), e.getMessage());
                failedOperations.add("POINT");
            }
        }

        if (!failedOperations.isEmpty()) {
            log.warn("주문 후처리 일부 실패. 재처리 필요 항목: {}", failedOperations);
            order.setPendingOperations(failedOperations);
        }
    }

    private boolean isCancelable(DeliveryStatus status) {
        return status == DeliveryStatus.WAITING || status == DeliveryStatus.PENDING;
    }

    // [수정] orderId 파라미터 추가
    private void processWaitingOrderCancellation(Order order) {
        // 1. PG 환불
        if (order.getPaymentKey() != null) {
            try {
                PaymentCancelRequest cancelRequest = new PaymentCancelRequest("사용자 주문 취소", order.getPaymentAmount());
                paymentClient.cancelPayment(order.getPaymentKey(), cancelRequest);
            } catch (Exception e) {
                log.error("PG 결제 취소 실패: OrderID={}, Error={}", order.getId(), e.getMessage());
                throw new OrderException(OrderErrorCode.PAYMENT_CANCEL_FAILED);
            }
        }

        // 2. 포인트 환불 (orderId 추가)
        if (order.getPointDiscount() != null && order.getPointDiscount() > 0) {
            try {
                memberClient.cancelPoint(order.getUserId(), order.getPointDiscount(), order.getId());
            } catch (Exception e) {
                log.error("포인트 취소 실패: OrderID={}, Error={}", order.getId(), e.getMessage());
                throw new OrderException(OrderErrorCode.MEMBER_SERVICE_ERROR);
            }
        }

        // 3. 쿠폰 복구
        if (order.getCouponId() != null) {
            try {
                MemberCouponCancelRequest cancelReq = new MemberCouponCancelRequest(order.getCouponId());
                couponClient.cancelCouponUsage(order.getUserId(), cancelReq);
            } catch (Exception e) {
                log.error("쿠폰 취소 실패: OrderID={}, Error={}", order.getId(), e.getMessage());
                throw new OrderException(OrderErrorCode.COUPON_SERVICE_ERROR);
            }
        }

        // 4. 재고 복구
        try {
            List<StockRequest> restoreRequests = order.getOrderItems().stream()
                    .map(item -> new StockRequest(item.getBookId(), item.getQuantity()))
                    .toList();
            String idempotencyKey = order.getId() + "-restore";
            bookClient.restoreStock(restoreRequests, idempotencyKey);

        } catch (Exception e) {
            log.error("재고 복구 실패 (WAITING 취소): OrderID={}", order.getId(), e);
            throw new OrderException(OrderErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    // [수정] orderId 파라미터 추가
    private void processPendingOrderCancellation(Order order) {
        if (order.getPointDiscount() != null && order.getPointDiscount() > 0) {
            try {
                memberClient.cancelPoint(order.getUserId(), order.getPointDiscount(), order.getId());
            } catch (Exception e) {
                log.error("CRITICAL: 포인트 예약 취소 실패! OrderID={}", order.getId(), e);
                throw new OrderException(OrderErrorCode.MEMBER_SERVICE_ERROR);
            }
        }

        try {
            List<Long> bookIds = order.getOrderItems().stream().map(OrderItem::getBookId).toList();
            bookClient.releaseHeldStock(bookIds, order.getOrderKey());

        } catch (Exception e) {
            log.error("CRITICAL: 재고 선점 해제 실패 (PENDING 취소): OrderID={}, OrderKey={}", order.getId(), order.getOrderKey(), e);
            throw new OrderException(OrderErrorCode.EXTERNAL_SERVICE_ERROR);
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

    @Builder
    private record OrderCalculationData(List<OrderItem> tempOrderItems, int totalProductAmount, int totalWrappingFee,
                                        int totalEarnedPoint, String firstBookTitle) {
    }
}