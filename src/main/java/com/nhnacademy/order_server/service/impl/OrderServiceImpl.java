package com.nhnacademy.order_server.service.impl;

import com.nhnacademy.order_server.adapter.*;
import com.nhnacademy.order_server.dto.message.PaymentSuccessMessage;
import com.nhnacademy.order_server.dto.request.*;
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
import java.time.LocalDateTime;
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

        // 1. 재고 선점
        List<Long> heldStockBookIds = request.getOrderItems().stream()
                .map(OrderCreateRequest.OrderItemRequest::getBookId)
                .distinct()
                .toList();

        try {
            double earnRate = getMemberEarnRate(userId);
            OrderCalculationData orderData = processOrderItemsAndHoldStock(request, earnRate, orderKey);

            int deliveryFee = calculateDeliveryFee(orderData.totalProductAmount(), request.getReceiverAddress());
            OrderCreateRequest.OrderCalculationResult calculationResult = calculateFinalAmounts(request, orderData, deliveryFee);

            // 2. 주문 저장 (초기 상태: PAYMENT_WAITING)
            Order order = saveOrder(request, calculationResult, orderKey, encryptedPassword, orderData.tempOrderItems());

            // [중요] 엔티티 생성 시 디폴트가 다를 수 있으므로 명시적으로 상태 설정
            if (order.getDeliveryStatus() == null) {
                order.updateStatus(DeliveryStatus.PAYMENT_WAITING);
            }

            // 3. 포인트 예약 (orderId 전달)
            reservePointsIfMember(userId, usedPoint, order.getId());

            // 4. 배송 정보 저장
            saveDelivery(order, request.getRequestDeliveryDate());

            // 5. 장바구니 비우기
            clearCartSilently(userId);

            return createOrderResponse(order, orderData.firstBookTitle(), request.getOrderItems().size());

        } catch (Exception e) {
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

        // [변경] PENDING -> PAYMENT_WAITING (결제 대기 상태인지 확인)
        if (order.getDeliveryStatus() != DeliveryStatus.PAYMENT_WAITING) {
            log.info("이미 처리된 주문입니다. (Idempotency Check) OrderID={}, Status={}", orderId, order.getDeliveryStatus());
            return;
        }

        if (order.getPaymentAmount() != message.getTotalAmount().intValue()) {
            log.error("주문 금액 불일치! OrderID={}, OrderAmount={}, PaidAmount={}", orderId, order.getPaymentAmount(), message.getTotalAmount());
            throw new OrderException(OrderErrorCode.INVALID_REQUEST);
        }

        // [변경] WAITING -> PREPARING (배송 준비 중으로 변경)
        order.updateStatus(DeliveryStatus.PREPARING);
        order.setPaymentKey(message.getPaymentKey());

        finalizeExternalResources(order);
        log.info("RabbitMQ 결제 메시지 처리 완료. OrderID={}", orderId);
    }


    @Override
    @Transactional
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!isCancelable(order.getDeliveryStatus())) {
            throw new OrderException(OrderErrorCode.CANNOT_CANCEL_ORDER);
        }

        if (order.getDeliveryStatus() == DeliveryStatus.PREPARING) {
            processPreparingOrderCancellation(order);
        }
        else if (order.getDeliveryStatus() == DeliveryStatus.PAYMENT_WAITING) {
            processPaymentWaitingOrderCancellation(order);
        }

        order.updateStatus(DeliveryStatus.CANCELED);
    }


    // OrderServiceImpl 내부에 추가

    @Transactional
    public void cancelExpiredOrders() {
        // 예: 24시간이 지난 주문은 취소
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);

        // 1. 대상 조회
        List<Order> expiredOrders = orderRepository.findByDeliveryStatusAndCreatedAtBefore(
                DeliveryStatus.PAYMENT_WAITING,
                threshold
        );

        log.info("결제 대기 만료 주문 취소 시작: 총 {}건", expiredOrders.size());

        for (Order order : expiredOrders) {
            try {
                // 기존에 만들어둔 '결제 대기 중 취소' 로직 재활용
                // 이 메서드 내부에서 포인트 취소(cancelPoint)와 재고 해제(releaseHeldStock)를 수행함
                processPaymentWaitingOrderCancellation(order);

                // 상태 변경 (CANCELED)
                order.updateStatus(DeliveryStatus.CANCELED);

                log.info("만료 주문 취소 완료: OrderID={}", order.getId());
            } catch (Exception e) {
                log.error("만료 주문 취소 중 오류 발생: OrderID={}, Error={}", order.getId(), e.getMessage());
                // 한 건 실패해도 나머지는 계속 진행되도록 예외를 잡아서 로깅만 함
            }
        }
    }

    // --- Private Methods ---

    private String validateAndEncryptPassword(OrderCreateRequest request) {
        if (request.getUserId() == null) {
            if (request.getOrderPassword() == null || request.getOrderPassword().isBlank()) {
                throw new OrderException(OrderErrorCode.ORDER_PASSWORD_REQUIRED);
            }
            return passwordEncoder.encode(request.getOrderPassword());
        }
        return null;
    }

    private void reservePointsIfMember(Long userId, int usedPoint, Long orderId) {
        if (userId != null && usedPoint > 0) {
            try {
                memberClient.reservePoint(userId, usedPoint, orderId);
            } catch (Exception e) {
                log.error("포인트 예약 실패 UserID={}: {}", userId, e.getMessage());
                throw new OrderException(OrderErrorCode.NOT_ENOUGH_POINT);
            }
        }
    }

    // ... (중략: getMemberEarnRate, processOrderItemsAndHoldStock, getWrapperMap, getBookInfoMap, calculateDeliveryFee, calculateFinalAmounts) ...
    // 위 메서드들은 상태값과 무관하므로 기존 코드 그대로 유지하면 됩니다.
    private double getMemberEarnRate(Long userId) {
        if (userId == null) return 0.0;
        try {
            MemberGradeResponse gradeInfo = memberClient.getMemberGrade(userId);
            return gradeInfo.getEarnRate();
        } catch (Exception e) {
            return 0.0;
        }
    }

    private OrderCalculationData processOrderItemsAndHoldStock(OrderCreateRequest request, double earnRate, String orderKey) {
        // (기존 코드와 동일)
        Map<Long, Wrapper> wrapperMap = getWrapperMap(request.getOrderItems());
        Map<Long, BookInfoResponse> bookInfoMap = getBookInfoMap(request.getOrderItems());
        Map<String, Integer> mergedQuantityMap = new LinkedHashMap<>();
        Map<String, Long> keyToBookIdMap = new HashMap<>();
        Map<String, Long> keyToWrapperIdMap = new HashMap<>();
        String firstBookTitle = null;
        int totalProductAmount = 0;
        int totalWrappingFee = 0;
        int totalEarnedPoint = 0;

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
                    .bookId(bookId).bookTitle(bookInfo.getTitle()).quantity(totalQty)
                    .unitPrice(bookInfo.getPrice()).wrapper(wrapper).isWrapped(wrapper != null)
                    .key(UUID.randomUUID().toString()).build();
            finalOrderItems.add(orderItem);
        }

        List<StockRequest> stockRequests = stockMap.entrySet().stream()
                .map(e -> new StockRequest(e.getKey(), e.getValue())).toList();
        try {
            if (!stockRequests.isEmpty()) {
                bookClient.holdStockBatch(stockRequests, orderKey);
            }
        } catch (Exception e) {
            log.error("Batch Stock hold failed: OrderKey={}", orderKey, e);
            throw new OrderException(OrderErrorCode.OUT_OF_STOCK);
        }
        return OrderCalculationData.builder()
                .tempOrderItems(finalOrderItems).totalProductAmount(totalProductAmount)
                .totalWrappingFee(totalWrappingFee).totalEarnedPoint(totalEarnedPoint)
                .firstBookTitle(firstBookTitle).build();
    }

    private Map<Long, Wrapper> getWrapperMap(List<OrderCreateRequest.OrderItemRequest> items) {
        Set<Long> wrapperIds = items.stream().map(OrderCreateRequest.OrderItemRequest::getWrapperId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (wrapperIds.isEmpty()) return Collections.emptyMap();
        return wrapperRepository.findAllById(wrapperIds).stream().collect(Collectors.toMap(Wrapper::getId, Function.identity()));
    }

    private Map<Long, BookInfoResponse> getBookInfoMap(List<OrderCreateRequest.OrderItemRequest> items) {
        List<Long> bookIds = items.stream().map(OrderCreateRequest.OrderItemRequest::getBookId).distinct().toList();
        try {
            ResponseEntity<List<BookInfoResponse>> response = bookClient.getBooksBulk(bookIds);
            if (response == null || response.getBody() == null) return Collections.emptyMap();
            return response.getBody().stream().collect(Collectors.toMap(BookInfoResponse::getBookId, Function.identity(), (a, b) -> a));
        } catch (Exception e) {
            throw new OrderException(OrderErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    private int calculateDeliveryFee(int productAmount, String address) {
        try {
            return deliveryService.calculateDeliveryFee(productAmount, address);
        } catch (Exception e) {
            throw new OrderException(OrderErrorCode.DELIVERY_FEE_CALCULATION_ERROR);
        }
    }

    private OrderCreateRequest.OrderCalculationResult calculateFinalAmounts(OrderCreateRequest request, OrderCalculationData data, int deliveryFee) {
        int couponDiscount = 0;
        if (request.getCouponId() != null) {
            try {
                CouponCalculationRequest calcReq = new CouponCalculationRequest(request.getCouponId(), (long) data.totalProductAmount());
                if (request.getUserId() != null) {
                    CouponCalculationResponse response = couponClient.calculateCoupon(request.getUserId(), calcReq);
                    if (response != null && response.getDiscountAmount() != null) couponDiscount = response.getDiscountAmount().intValue();
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
                .productAmount(data.totalProductAmount()).deliveryFee(deliveryFee).wrappingFee(data.totalWrappingFee())
                .couponDiscount(couponDiscount).pointDiscount(usedPoint).paymentAmount(finalPaymentAmount)
                .earnedPoint(data.totalEarnedPoint()).build();
    }

    private Order saveOrder(OrderCreateRequest request, OrderCreateRequest.OrderCalculationResult result, String orderKey, String encryptedPassword, List<OrderItem> items) {
        Order order = request.toEntity(result, orderKey, encryptedPassword);
        items.forEach(order::addOrderItem);
        return orderRepository.save(order);
    }

    private void saveDelivery(Order order, LocalDate requestDate) {
        LocalDate date = (requestDate != null) ? requestDate : LocalDate.now().plusDays(DEFAULT_DELIVERY_DAYS);
        Delivery delivery = Delivery.builder().order(order).requestDeliveryDate(date).estimatedDeliveryDate(date).build();
        deliveryRepository.save(delivery);
    }

    private void clearCartSilently(Long userId) {
        if (userId != null) {
            try { cartClient.clearCart(userId); } catch (Exception e) { log.warn("장바구니 비우기 실패: User={}", userId); }
        }
    }

    private OrderCreateResponse createOrderResponse(Order order, String firstBookTitle, int totalItems) {
        return OrderCreateResponse.from(order, firstBookTitle, totalItems);
    }

    private void compensateTransaction(Long userId, int usedPoint, List<Long> heldStockBookIds, String orderKey, Exception originalException) {
        if (userId != null && usedPoint > 0) {
            try { memberClient.cancelPoint(userId, usedPoint, 0L); } catch (Exception e) { log.error("CRITICAL: 포인트 예약 취소 실패!", e); }
        }
        if (!heldStockBookIds.isEmpty()) {
            try { bookClient.releaseHeldStock(heldStockBookIds, orderKey); } catch (Exception e) { log.error("CRITICAL: 재고 롤백 실패!", e); }
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
                    .paymentKey(paymentKey).orderKey(order.getOrderKey())
                    .amount(order.getPaymentAmount()).paymentMethod("Toss").build();
            paymentClient.confirmPayment(confirmRequest);
        } catch (Exception e) {
            log.error("결제 승인 실패", e);
            throw new OrderException(OrderErrorCode.EXTERNAL_API_ERROR);
        }
    }

    private void finalizeExternalResources(Order order) {
        List<String> failedOperations = new ArrayList<>();
        try {
            List<Long> bookIds = new ArrayList<>();
            for (OrderItem item : order.getOrderItems()) {
                for (int i = 0; i < item.getQuantity(); i++) bookIds.add(item.getBookId());
            }
            bookClient.confirmStockDeduction(bookIds, order.getOrderKey());
        } catch (Exception e) {
            log.error("재고 확정 실패", e);
            failedOperations.add("STOCK");
        }

        if (order.getCouponId() != null) {
            try {
                MemberCouponUseRequest useReq = new MemberCouponUseRequest(order.getCouponId(), order.getId());
                couponClient.useCoupon(order.getUserId(), useReq);
            } catch (Exception e) {
                log.error("쿠폰 사용 확정 실패", e);
                failedOperations.add("COUPON");
            }
        }

        if (order.getPointDiscount() != null && order.getPointDiscount() > 0) {
            try {
                memberClient.confirmPoint(order.getUserId(), order.getPointDiscount(), order.getId());
            } catch (Exception e) {
                log.error("포인트 확정 실패", e);
                failedOperations.add("POINT");
            }
        }

        if (!failedOperations.isEmpty()) order.setPendingOperations(failedOperations);
    }

    private boolean isCancelable(DeliveryStatus status) {
        return status == DeliveryStatus.PREPARING || status == DeliveryStatus.PAYMENT_WAITING;
    }

    // [이름 변경] processWaitingOrderCancellation -> processPreparingOrderCancellation
    private void processPreparingOrderCancellation(Order order) {
        if (order.getPaymentKey() != null) {
            try {
                PaymentCancelRequest cancelRequest = new PaymentCancelRequest("사용자 주문 취소", order.getPaymentAmount());
                paymentClient.cancelPayment(order.getPaymentKey(), cancelRequest);
            } catch (Exception e) {
                throw new OrderException(OrderErrorCode.PAYMENT_CANCEL_FAILED);
            }
        }
        if (order.getPointDiscount() != null && order.getPointDiscount() > 0) {
            try { memberClient.cancelPoint(order.getUserId(), order.getPointDiscount(), order.getId()); }
            catch (Exception e) { throw new OrderException(OrderErrorCode.MEMBER_SERVICE_ERROR); }
        }
        if (order.getCouponId() != null) {
            try {
                MemberCouponCancelRequest cancelReq = new MemberCouponCancelRequest(order.getCouponId(), order.getId());
                couponClient.cancelCouponUsage(order.getUserId(), cancelReq);
            } catch (Exception e) { throw new OrderException(OrderErrorCode.COUPON_SERVICE_ERROR); }
        }
        try {
            List<StockRequest> restoreRequests = order.getOrderItems().stream()
                    .map(item -> new StockRequest(item.getBookId(), item.getQuantity())).toList();
            bookClient.restoreStock(restoreRequests, order.getId() + "-restore");
        } catch (Exception e) { throw new OrderException(OrderErrorCode.EXTERNAL_SERVICE_ERROR); }
    }

    // [이름 변경] processPendingOrderCancellation -> processPaymentWaitingOrderCancellation
    private void processPaymentWaitingOrderCancellation(Order order) {
        if (order.getPointDiscount() != null && order.getPointDiscount() > 0) {
            try {
                memberClient.cancelPoint(order.getUserId(), order.getPointDiscount(), order.getId());
            } catch (Exception e) {
                log.error("포인트 예약 취소 실패 (결제 대기 중 취소): OrderID={}", order.getId(), e);
            }
        }
        try {
            List<Long> bookIds = order.getOrderItems().stream().map(OrderItem::getBookId).toList();
            bookClient.releaseHeldStock(bookIds, order.getOrderKey());
        } catch (Exception e) {
            log.error("재고 선점 해제 실패 (결제 대기 중 취소): OrderID={}", order.getId(), e);
        }
    }

    @Override
    public Page<OrderResponse> getMyOrders(Long userId, Pageable pageable) {
        return orderRepository.findAllByUserId(userId, pageable).map(OrderResponse::from);
    }

    @Override
    public OrderResponse getOrderDetail(Long orderId) {
        Order order = orderRepository.findByIdWithItems(orderId).orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));
        return OrderResponse.from(order);
    }

    @Override
    public OrderResponse getGuestOrder(Long orderId, Integer password) {
        Order order = orderRepository.findByIdAndOrderPassword(orderId, password).orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));
        return OrderResponse.from(order);
    }

    @Override
    public OrderValidationInfoResponse getValidationInfo(String orderKey) {
        Order order = orderRepository.findByOrderKey(orderKey).orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));
        return OrderValidationInfoResponse.from(order);
    }

    @Builder
    private record OrderCalculationData(List<OrderItem> tempOrderItems, int totalProductAmount, int totalWrappingFee, int totalEarnedPoint, String firstBookTitle) {}
}