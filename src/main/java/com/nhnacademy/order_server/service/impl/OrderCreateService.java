package com.nhnacademy.order_server.service.impl;

import com.nhnacademy.order_server.adapter.CartClient;
import com.nhnacademy.order_server.adapter.MemberClient;
import com.nhnacademy.order_server.dto.OrderCalculationData;
import com.nhnacademy.order_server.dto.request.OrderCreateRequest;
import com.nhnacademy.order_server.dto.request.PointTransactionRequest;
import com.nhnacademy.order_server.dto.response.OrderCreateResponse;
import com.nhnacademy.order_server.entity.Delivery;
import com.nhnacademy.order_server.entity.Order;
import com.nhnacademy.order_server.entity.OrderItem;
import com.nhnacademy.order_server.entity.enums.DeliveryStatus;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import com.nhnacademy.order_server.repository.DeliveryRepository;
import com.nhnacademy.order_server.repository.OrderRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCreateService {

    private final OrderRepository orderRepository;
    private final DeliveryRepository deliveryRepository;
    private final MemberClient memberClient;
    private final CartClient cartClient;
    private final PasswordEncoder passwordEncoder;

    private static final int DEFAULT_DELIVERY_DAYS = 2;

    /**
     * 실제 DB 트랜잭션이 일어나는 메서드입니다.
     * 외부(OrderServiceImpl)에서 호출하므로 @Transactional이 정상 작동합니다.
     */
    @Transactional
    public OrderCreateResponse createOrderInTransaction(OrderCreateRequest request,
                                                        String orderKey,
                                                        OrderCalculationData orderData,
                                                        OrderCreateRequest.OrderCalculationResult result) {

        // 1. 비밀번호 검증 및 암호화
        String encryptedPassword = validateAndEncryptPassword(request);

        // 2. 주문 엔티티 저장
        Order order = saveOrder(request, result, orderKey, encryptedPassword, orderData.tempOrderItems());
        order.updateStatus(DeliveryStatus.PAYMENT_WAITING);

        // 3. 포인트 가승인 (TCC Reserve 호출)
        if (request.getUserId() != null && request.getUsedPoint() > 0) {
            memberClient.reservePoint(PointTransactionRequest.builder()
                    .memberId(request.getUserId())
                    .amount(Long.valueOf(request.getUsedPoint()))
                    .orderId(order.getId())
                    .build());
        }

        // 4. 배송 정보 저장
        saveDelivery(order, request.getRequestDeliveryDate());

        // 5. 장바구니 비우기 (실패해도 주문 생성은 진행)
        tryClearCart(request.getUserId());

        return OrderCreateResponse.from(order, orderData.firstBookTitle(), request.getOrderItems().size());
    }

    private String validateAndEncryptPassword(OrderCreateRequest r) {
        if (r.getUserId() == null) {
            if (r.getOrderPassword() == null || r.getOrderPassword().isBlank()) {
                throw new OrderException(OrderErrorCode.ORDER_PASSWORD_REQUIRED);
            }
            return passwordEncoder.encode(r.getOrderPassword());
        }
        return null;
    }

    private Order saveOrder(OrderCreateRequest request, OrderCreateRequest.OrderCalculationResult res,
                            String key, String pwd, List<OrderItem> items) {
        Order o = request.toEntity(res, key, pwd);
        items.forEach(o::addOrderItem);
        if (request.getCouponId() != null) {
            o.setCouponId(request.getCouponId());
        }
        return orderRepository.save(o);
    }

    private void saveDelivery(Order o, LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now().plusDays(DEFAULT_DELIVERY_DAYS);
        deliveryRepository.save(Delivery.builder()
                .order(o)
                .requestDeliveryDate(d)
                .estimatedDeliveryDate(d)
                .build());
    }

    private void tryClearCart(Long uid) {
        if (uid != null) {
            try {
                cartClient.clearCart(uid);
            } catch (Exception ignored) {
                log.warn("장바구니 비우기 실패 (사용자 ID: {})", uid);
            }
        }
    }
}