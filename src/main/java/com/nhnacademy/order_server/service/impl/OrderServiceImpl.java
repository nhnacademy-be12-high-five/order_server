package com.nhnacademy.order_server.service.impl;

import com.nhnacademy.order_server.adapter.BookClient;
import com.nhnacademy.order_server.adapter.CartClient;
import com.nhnacademy.order_server.adapter.CouponClient;
import com.nhnacademy.order_server.adapter.MemberClient;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.request.OrderReturnRequest;
import com.nhnacademy.order_server.dto.response.OrderResponse;
import com.nhnacademy.order_server.dto.response.OrderReturnCheckResponse;
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
    public Long createOrder(OrderCreateRequest request) {

        // 1. [검증] 비회원 검증
        if (request.getUserId() == null && request.getOrderPassword() == null) {
            throw new OrderException(OrderErrorCode.ORDER_PASSWORD_REQUIRED);
        }

        Long userId = request.getUserId();

        // 2. [MSA] 회원 등급별 적립률 조회
        double earnRate = getMemberEarnRate(userId);

        // 3. [메서드 분리] 상품 유효성 검사, 가차감 및 비용 계산
        OrderCalculationData orderData = calculateAndValidateOrderItems(request, earnRate);

        // 4. 배송비 계산
        int deliveryFee = deliveryService.calculateDeliveryFee(orderData.totalProductAmount, request.getReceiverAddress());

        // 5. [메서드 분리] 최종 할인 및 금액 계산
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

        // 9. [후처리] 장바구니 비우기 (Commitment)
        if (userId != null) {
            try {
                cartClient.clearCartByUserId(userId);
            } catch (Exception e) {
                log.warn("장바구니 비우기 요청 실패 (결과에 영향 없음): User={}", userId, e);
            }
        }

        return order.getId();
    }

    // --- 헬퍼 메서드 영역 ---

    // DTO 내부 클래스 대신 사용 (메서드 리턴 값으로 사용)
    private static class OrderCalculationData {
        final List<OrderItem> tempOrderItems;
        final int totalProductAmount;
        final int totalWrappingFee;
        final int totalEarnedPoint;
        // final String firstBookTitle; // 주문명 필요 시 추가

        public OrderCalculationData(List<OrderItem> tempOrderItems, int totalProductAmount, int totalWrappingFee, int totalEarnedPoint) {
            this.tempOrderItems = tempOrderItems;
            this.totalProductAmount = totalProductAmount;
            this.totalWrappingFee = totalWrappingFee;
            this.totalEarnedPoint = totalEarnedPoint;
        }
    }

    /**
     * [MSA 헬퍼] 회원 등급별 적립률을 조회합니다.
     */
    private double getMemberEarnRate(Long userId) {
        if (userId == null) return 0.0;
        try {
            // [Feign] 회원 서버: 등급 정보 및 적립률 조회
            MemberGradeResponse gradeInfo = memberClient.getMemberGrade(userId);
            return gradeInfo.getEarnRate();
        } catch (Exception e) {
            log.error("회원 등급 조회 실패 (UserId: {}): {}", userId, e.getMessage());
            // 회원 서버 오류는 주문 트랜잭션을 롤백해야 함
            throw new OrderException(OrderErrorCode.MEMBER_SERVICE_ERROR);
        }
    }

    /**
     * [계산] 주문 상품 유효성 검증, 가차감, 비용을 계산합니다. (N+1 문제 해결 포함)
     */
    private OrderCalculationData calculateAndValidateOrderItems(OrderCreateRequest request, double earnRate) {
        Set<Long> wrapperIds = request.getOrderItems().stream()
                .map(OrderCreateRequest.OrderItemRequest::getWrapperId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Wrapper> wrapperMap = wrapperRepository.findAllById(wrapperIds).stream()
                .collect(Collectors.toMap(Wrapper::getId, wrapper -> wrapper));

        List<OrderItem> tempOrderItems = new ArrayList<>();
        int totalProductAmount = 0;
        int totalWrappingFee = 0;
        int totalEarnedPoint = 0;

        for (OrderCreateRequest.OrderItemRequest itemReq : request.getOrderItems()) {
            // [Feign 1] 도서 서버: 가격 및 정보 조회 (Validation)
            BookInfoResponse bookInfo;
            try {
                bookInfo = bookClient.getBookInfo(itemReq.getBookId());
            } catch (Exception e) {
                throw new OrderException(OrderErrorCode.EXTERNAL_SERVICE_ERROR); // 도서 서버 장애
            }
            int bookPrice = bookInfo.getPrice();

            // [Feign 2] 도서 서버: 재고 확인 및 가차감 요청
            try {
                bookClient.holdStock(itemReq.getBookId(), itemReq.getQuantity());
            } catch (Exception e) {
                // 재고 부족(400)이나 통신 오류(500) 처리
                throw new OrderException(OrderErrorCode.OUT_OF_STOCK);
            }

            // 적립금 계산
            totalEarnedPoint += (int) (bookPrice * itemReq.getQuantity() * earnRate);

            // 포장지 처리
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

    /**
     * [계산] 최종 할인 및 결제 금액을 확정하고 DTO로 반환합니다.
     */
    private OrderCreateRequest.OrderCalculationResult calculateFinalAmounts(
            OrderCreateRequest request, int totalProductAmount, int totalWrappingFee, int deliveryFee, int totalEarnedPoint, Long userId) {

        int couponDiscount = 0;

        // 1. 쿠폰 할인 적용
        if (request.getCouponId() != null) {
            try {
                // [Feign 3] 쿠폰 서버: 할인액 계산
                couponDiscount = couponClient.calculateDiscount(request.getCouponId(), totalProductAmount);
            } catch (Exception e) {
                throw new OrderException(OrderErrorCode.COUPON_SERVICE_ERROR);
            }
            if (couponDiscount > totalProductAmount) couponDiscount = totalProductAmount;
        }

        // 2. 포인트 사용 검증
        int usedPoint = (request.getUsedPoint() != null) ? request.getUsedPoint() : 0;
        if (usedPoint > 0) {
            try {
                // [Feign 5] 회원 서버: 포인트 잔액 확인
                Integer balance = memberClient.getPointBalance(userId);
                if (balance < usedPoint) {
                    throw new OrderException(OrderErrorCode.NOT_ENOUGH_POINT);
                }
            } catch (Exception e) {
                throw new OrderException(OrderErrorCode.MEMBER_SERVICE_ERROR);
            }
        }

        // 3. 최종 금액 계산
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