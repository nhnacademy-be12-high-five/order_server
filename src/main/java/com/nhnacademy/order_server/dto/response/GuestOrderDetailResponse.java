package com.nhnacademy.order_server.dto.response;

import com.nhnacademy.order_server.entity.Order;
import com.nhnacademy.order_server.entity.OrderItem;
import com.nhnacademy.order_server.entity.Delivery;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestOrderDetailResponse {

    // 1. 주문 기본 정보
    private Long orderId;
    private String orderNumber;
    private LocalDateTime orderDate;
    private String statusName;

    // 2. 배송지 정보
    private String receiverName;
    private String receiverPhone;
    private String address;
    private String addressDetail;
    private String deliveryRequest;

    // 3. 결제 금액 정보 (Long 타입 통일)
    private Long totalAmount;
    private Long deliveryFee;
    private Long couponDiscount;
    private Long pointDiscount;
    private Long paymentAmount;

    // 4. 주문 상품 목록
    private List<GuestOrderItemResponse> orderItems;

    // [중요] Order 엔티티 -> DTO 변환 메서드
    public static GuestOrderDetailResponse from(Order order) {
        Delivery delivery = order.getDelivery(); // Order 엔티티에 Delivery 연관관계가 있다고 가정

        // 상품 목록 변환
        List<GuestOrderItemResponse> itemResponses = order.getOrderItems().stream()
                .map(GuestOrderItemResponse::from)
                .collect(Collectors.toList());

        // 배송 정보 안전하게 가져오기 (null 체크)
        String receiverName = (order.getReceiverName() != null) ? order.getReceiverName() : "";
        String addr = (order.getReceiverAddress() != null) ? order.getReceiverAddress() : "";
        // 배송비 계산 (Delivery 객체나 Order 필드 확인 필요)
        Long deliveryCost = (order.getDeliveryFee() != null) ? Long.valueOf(order.getDeliveryFee()) : 0L;

        return GuestOrderDetailResponse.builder()
                .orderId(order.getId())
                .orderNumber(String.valueOf(order.getId()))
                .orderDate(order.getOrderDate())
                .statusName(order.getDeliveryStatus().toString()) // 한글 변환 필요 시 로직 추가

                .receiverName(receiverName)
                .address(addr)
                .addressDetail("") // 상세주소 필드가 따로 없다면 공란 혹은 address에서 분리
                .deliveryRequest("") // 요청사항 필드가 없다면 공란

                // 금액 정보 (null이면 0으로 처리)
                .totalAmount(order.getPaymentAmount() != null ? Long.valueOf(order.getPaymentAmount()) : 0L)
                .deliveryFee(deliveryCost)
                .couponDiscount(order.getCouponDiscount() != null ? Long.valueOf(order.getCouponDiscount()) : 0L)
                .pointDiscount(order.getPointDiscount() != null ? Long.valueOf(order.getPointDiscount()) : 0L)
                .paymentAmount(order.getPaymentAmount() != null ? Long.valueOf(order.getPaymentAmount()) : 0L)

                .orderItems(itemResponses)
                .build();
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GuestOrderItemResponse {
        private String title;
        private Integer quantity;
        private Long price;
        private Long totalPrice;
        private String wrapperName;

        public static GuestOrderItemResponse from(OrderItem item) {
            String wName = (item.getWrapper() != null) ? item.getWrapper().getWrapperName() : null;

            return GuestOrderItemResponse.builder()
                    .title(item.getBookTitle())
                    .quantity(item.getQuantity())
                    .price((long) item.getUnitPrice())
                    .totalPrice(((long) item.getUnitPrice() * item.getQuantity()))
                    .wrapperName(wName)
                    .build();
        }
    }
}