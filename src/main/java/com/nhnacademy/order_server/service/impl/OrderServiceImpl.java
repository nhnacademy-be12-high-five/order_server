package com.nhnacademy.order_server.service.impl;

import com.nhnacademy.order_server.adapter.*;
import com.nhnacademy.order_server.dto.OrderCalculationData;
import com.nhnacademy.order_server.dto.message.PaymentSuccessMessage;
import com.nhnacademy.order_server.dto.request.*;
import com.nhnacademy.order_server.dto.response.*;
import com.nhnacademy.order_server.dto.response.external.BookInfoResponse;
import com.nhnacademy.order_server.dto.response.external.MemberGradeResponse;
import com.nhnacademy.order_server.entity.*;
import com.nhnacademy.order_server.entity.enums.DeliveryStatus;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import com.nhnacademy.order_server.repository.*;
import com.nhnacademy.order_server.service.DeliveryService;
import com.nhnacademy.order_server.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
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
    private final RabbitTemplate rabbitTemplate;

    @Autowired
    @Lazy
    private OrderService self; // REQUIRES_NEW 트랜잭션을 위한 자기 참조 호출

    private static final int DEFAULT_DELIVERY_DAYS = 2;

    // =====================================================================================
    // 1. CREATE (주문 생성 및 트랜잭션 처리)
    // =====================================================================================

    @Override
    public OrderCreateResponse createOrder(OrderCreateRequest request) {
        String orderKey = UUID.randomUUID().toString();
        double earnRate = getMemberEarnRate(request.getUserId());

        // 1-1. 상품 정보 조회 및 재고 선점
        OrderCalculationData orderData = processOrderItemsAndHoldStock(request, earnRate, orderKey);

        // 1-2. 최종 결제 금액 계산
        int deliveryFee = calculateDeliveryFee(orderData.totalProductAmount(), request.getReceiverAddress());
        OrderCreateRequest.OrderCalculationResult result = calculateFinalAmounts(request, orderData, deliveryFee);

        try {
            // 1-3. DB 저장 및 외부 연동 (포인트/쿠폰 예약) - 별도 트랜잭션 수행
            return self.createOrderTransactional(request, orderKey, orderData, result);
        } catch (Exception e) {
            compensateTransaction(request.getUserId(), request.getUsedPoint(), orderData, orderKey);
            throw e;
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderCreateResponse createOrderTransactional(OrderCreateRequest request, String orderKey,
                                                        OrderCalculationData orderData,
                                                        OrderCreateRequest.OrderCalculationResult result) {
        String encryptedPassword = validateAndEncryptPassword(request);

        // 주문 엔티티 저장
        Order order = saveOrder(request, result, orderKey, encryptedPassword, orderData.tempOrderItems());
        order.updateStatus(DeliveryStatus.PAYMENT_WAITING);

        // 쿠폰 사용 및 포인트 예약 (회원인 경우)
        if (order.getCouponId() != null) {
            couponClient.useCoupon(order.getUserId(), new MemberCouponUseRequest(request.getCouponId(), order.getId()));
        }
        if (request.getUserId() != null && request.getUsedPoint() > 0) {
            memberClient.reservePoint(request.getUserId(), request.getUsedPoint(), order.getId());
        }

        saveDelivery(order, request.getRequestDeliveryDate());
        tryClearCart(request.getUserId());

        return OrderCreateResponse.from(order, orderData.firstBookTitle(), request.getOrderItems().size());
    }

    // =====================================================================================
    // 2. READ (목록 조회, 상세 조회 및 통계 집계)
    // =====================================================================================

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrders(Long userId, Pageable pageable) {
        return orderRepository.findAllByUserId(userId, pageable).map(OrderResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderDetail(Long orderId) {
        return orderRepository.findByIdWithItems(orderId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public GuestOrderDetailResponse getGuestOrder(Long orderId, String password) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!passwordEncoder.matches(password, order.getOrderPassword())) {
            throw new OrderException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        return GuestOrderDetailResponse.from(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderAggregationDto> getOrderAggregations(LocalDateTime start, LocalDateTime end) {
        // [N+1 해결] 회원 등급 산정용 벌크 집계 (DB 레벨 GROUP BY)
        return orderRepository.findOrderAggregations(start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public Long getTotalPaymentAmount(Long userId, LocalDateTime since) {
        // 특정 기간 구매 확정 금액 숫자 합계만 반환
        Long total = orderRepository.sumPaymentAmountByUserId(userId, since);
        return total != null ? total : 0L;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrdersLast3Months(Long userId, Pageable pageable) {
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);
        return orderRepository.findByUserIdAndOrderDateAfter(userId, threeMonthsAgo, pageable).map(OrderResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderValidationInfoResponse getValidationInfo(String orderKey) {
        return orderRepository.findByOrderKey(orderKey)
                .map(OrderValidationInfoResponse::from)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPurchasedBook(Long memberId, Long bookId) {
        return orderRepository.hasPurchasedBook(memberId, bookId);
    }

    // =====================================================================================
    // 3. UPDATE (결제 성공 처리 및 구매 확정)
    // =====================================================================================

    @Override
    public void processPaymentSuccessMessage(PaymentSuccessMessage message) {
        Order order = orderRepository.findById(message.getOrderId())
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        if (order.getDeliveryStatus() != DeliveryStatus.PAYMENT_WAITING) return;

        order.updateStatus(DeliveryStatus.PREPARING);
        order.setPaymentKey(message.getPaymentKey());

        finalizeExternalResources(order); // 재고 차감 확정 및 포인트 확정
    }

    @Override
    public void purchaseConfirm(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));
        if (order.getDeliveryStatus() == DeliveryStatus.PURCHASE_CONFIRMED) return;

        order.updateStatus(DeliveryStatus.PURCHASE_CONFIRMED);
        if (order.getUserId() != null) {
            sendPointEarnMessage(order); // 비동기 포인트 적립
        }
    }

    // =====================================================================================
    // 4. CANCEL & BATCH (주문 취소 및 자동화 스케줄러)
    // =====================================================================================

    @Override
    public void cancelOrder(Long orderId) {
        self.cancelOrderTransactional(orderId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelOrderTransactional(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));
        if (!isCancelable(order.getDeliveryStatus())) throw new OrderException(OrderErrorCode.CANNOT_CANCEL_ORDER);

        if (order.getDeliveryStatus() == DeliveryStatus.PREPARING) {
            processPreparingOrderCancellation(order);
        } else {
            processPaymentWaitingOrderCancellation(order);
        }
        order.updateStatus(DeliveryStatus.CANCELED);
    }

    @Override
    public void autoCompleteDelivery() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(3);
        orderRepository.findByDeliveryStatusAndDelivery_ActualShipDateBefore(DeliveryStatus.DELIVERING, threshold)
                .forEach(order -> {
                    order.updateStatus(DeliveryStatus.DELIVERY_COMPLETED);
                    if (order.getDelivery() != null) order.getDelivery().completeDelivery();
                });
    }

    @Override
    public void autoConfirmPurchase() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(10);
        orderRepository.findByDeliveryStatusAndDelivery_ActualCompletionDateBefore(DeliveryStatus.DELIVERY_COMPLETED, threshold)
                .forEach(order -> this.purchaseConfirm(order.getId()));
    }

    @Override
    public void cancelExpiredOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        orderRepository.findByDeliveryStatusAndOrderDateBefore(DeliveryStatus.PAYMENT_WAITING, threshold)
                .forEach(order -> {
                    try {
                        processPaymentWaitingOrderCancellation(order);
                        order.updateStatus(DeliveryStatus.CANCELED);
                    } catch (Exception ignored) {}
                });
    }

    // =====================================================================================
    // 5. PRIVATE HELPERS
    // =====================================================================================

    private double getMemberEarnRate(Long userId) {
        if (userId == null) return 0.0;
        try {
            return memberClient.getMemberGrade(userId).getEarnRate();
        } catch (Exception e) {
            return 0.0;
        }
    }

    private OrderCalculationData processOrderItemsAndHoldStock(OrderCreateRequest request, double earnRate, String orderKey) {
        Map<Long, BookInfoResponse> bookInfoMap = getBookInfoMap(request.getOrderItems());
        Map<Long, Wrapper> wrapperMap = getWrapperMap(request.getOrderItems());

        List<OrderItem> finalOrderItems = new ArrayList<>();
        List<StockRequest> stockRequests = new ArrayList<>();
        int totalProductAmount = 0;
        int totalWrappingFee = 0;

        for (OrderCreateRequest.OrderItemRequest itemReq : request.getOrderItems()) {
            BookInfoResponse book = bookInfoMap.get(itemReq.getBookId());
            Wrapper wrapper = (itemReq.getWrapperId() != null) ? wrapperMap.get(itemReq.getWrapperId()) : null;

            totalProductAmount += book.getPrice() * itemReq.getQuantity();
            if (wrapper != null) totalWrappingFee += wrapper.getWrapperPrice() * itemReq.getQuantity();

            finalOrderItems.add(OrderItem.builder().bookId(book.getBookId()).bookTitle(book.getTitle())
                    .quantity(itemReq.getQuantity()).unitPrice(book.getPrice()).wrapper(wrapper)
                    .isWrapped(wrapper != null).key(UUID.randomUUID().toString()).build());

            stockRequests.add(new StockRequest(book.getBookId(), itemReq.getQuantity()));
        }

        bookClient.holdStockBatch(stockRequests, orderKey);

        return OrderCalculationData.builder().tempOrderItems(finalOrderItems).totalProductAmount(totalProductAmount)
                .totalWrappingFee(totalWrappingFee).totalEarnedPoint((int)(totalProductAmount * earnRate))
                .firstBookTitle(finalOrderItems.get(0).getBookTitle()).build();
    }

    private OrderCreateRequest.OrderCalculationResult calculateFinalAmounts(OrderCreateRequest request, OrderCalculationData data, int deliveryFee) {
        int couponDiscount = calculateCouponDiscount(request, data.totalProductAmount());
        int usedPoint = (request.getUsedPoint() != null) ? request.getUsedPoint() : 0;
        int finalPayment = Math.max(0, (data.totalProductAmount() + data.totalWrappingFee() + deliveryFee) - couponDiscount - usedPoint);

        return OrderCreateRequest.OrderCalculationResult.builder().productAmount(data.totalProductAmount())
                .deliveryFee(deliveryFee).wrappingFee(data.totalWrappingFee()).couponDiscount(couponDiscount)
                .pointDiscount(usedPoint).paymentAmount(finalPayment).earnedPoint(data.totalEarnedPoint()).build();
    }

    private int calculateCouponDiscount(OrderCreateRequest request, int totalProductAmount) {
        if (request.getCouponId() == null || request.getUserId() == null) return 0;
        try {
            CouponCalculationResponse resp = couponClient.calculateCoupon(request.getUserId(),
                    new CouponCalculationRequest(request.getCouponId(), (long) totalProductAmount));
            return (resp != null && resp.getDiscountAmount() != null) ? resp.getDiscountAmount().intValue() : 0;
        } catch (Exception e) {
            throw new OrderException(OrderErrorCode.COUPON_SERVICE_ERROR);
        }
    }

    private Order saveOrder(OrderCreateRequest request, OrderCreateRequest.OrderCalculationResult result,
                            String orderKey, String encryptedPassword, List<OrderItem> items) {
        Order order = request.toEntity(result, orderKey, encryptedPassword);
        items.forEach(order::addOrderItem);
        if (request.getCouponId() != null) order.setCouponId(request.getCouponId());
        return orderRepository.save(order);
    }

    private void saveDelivery(Order order, LocalDate requestDate) {
        LocalDate date = (requestDate != null) ? requestDate : LocalDate.now().plusDays(DEFAULT_DELIVERY_DAYS);
        deliveryRepository.save(Delivery.builder().order(order).requestDeliveryDate(date).estimatedDeliveryDate(date).build());
    }

    private void sendPointEarnMessage(Order order) {
        rabbitTemplate.convertAndSend("point-queue", PointEarnRequest.builder().memberId(order.getUserId())
                .eventType("EARN_ORDER").pureAmount(order.getPaymentAmount()).orderId(order.getId()).build());
    }

    private void finalizeExternalResources(Order order) {
        List<Long> bookIds = order.getOrderItems().stream()
                .flatMap(i -> Collections.nCopies(i.getQuantity(), i.getBookId()).stream()).toList();
        bookClient.confirmStockDeduction(bookIds, order.getOrderKey());
        if (order.getPointDiscount() != null && order.getPointDiscount() > 0) {
            memberClient.confirmPoint(order.getUserId(), order.getPointDiscount(), order.getId());
        }
    }

    private void processPreparingOrderCancellation(Order order) {
        if (order.getPaymentKey() != null) paymentClient.cancelPayment(order.getPaymentKey(), new PaymentCancelRequest("취소", order.getPaymentAmount()));
        if (order.getPointDiscount() != null && order.getPointDiscount() > 0) memberClient.cancelPoint(order.getUserId(), order.getPointDiscount(), order.getId());
        if (order.getCouponId() != null) couponClient.cancelCouponUsage(order.getUserId(), new MemberCouponCancelRequest(order.getCouponId(), order.getId()));
        bookClient.restoreStock(order.getOrderItems().stream().map(i -> new StockRequest(i.getBookId(), i.getQuantity())).toList(), order.getId() + "-restore");
    }

    private void processPaymentWaitingOrderCancellation(Order order) {
        if (order.getPointDiscount() != null && order.getPointDiscount() > 0) memberClient.cancelPoint(order.getUserId(), order.getPointDiscount(), order.getId());
        bookClient.releaseHeldStock(order.getOrderItems().stream().map(OrderItem::getBookId).toList(), order.getOrderKey());
    }

    private void compensateTransaction(Long userId, Integer usedPoint, OrderCalculationData data, String orderKey) {
        if (userId != null && usedPoint != null && usedPoint > 0) try { memberClient.cancelPoint(userId, usedPoint, 0L); } catch (Exception ignored) {}
        if (data != null) try { bookClient.releaseHeldStock(data.tempOrderItems().stream().map(OrderItem::getBookId).toList(), orderKey); } catch (Exception ignored) {}
    }

    private String validateAndEncryptPassword(OrderCreateRequest request) {
        if (request.getUserId() == null) {
            if (request.getOrderPassword() == null || request.getOrderPassword().isBlank()) throw new OrderException(OrderErrorCode.ORDER_PASSWORD_REQUIRED);
            return passwordEncoder.encode(request.getOrderPassword());
        }
        return null;
    }

    private Map<Long, BookInfoResponse> getBookInfoMap(List<OrderCreateRequest.OrderItemRequest> items) {
        ResponseEntity<List<BookInfoResponse>> resp = bookClient.getBooksBulk(items.stream().map(OrderCreateRequest.OrderItemRequest::getBookId).distinct().toList());
        return Objects.requireNonNull(resp.getBody()).stream().collect(Collectors.toMap(BookInfoResponse::getBookId, Function.identity()));
    }

    private Map<Long, Wrapper> getWrapperMap(List<OrderCreateRequest.OrderItemRequest> items) {
        Set<Long> ids = items.stream().map(OrderCreateRequest.OrderItemRequest::getWrapperId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap();
        return wrapperRepository.findAllById(ids).stream().collect(Collectors.toMap(Wrapper::getId, Function.identity()));
    }

    private void tryClearCart(Long userId) { if (userId != null) try { cartClient.clearCart(userId); } catch (Exception ignored) {} }
    private int calculateDeliveryFee(int productAmount, String address) { return deliveryService.calculateDeliveryFee(productAmount, address); }
    private boolean isCancelable(DeliveryStatus status) { return status == DeliveryStatus.PREPARING || status == DeliveryStatus.PAYMENT_WAITING; }
}