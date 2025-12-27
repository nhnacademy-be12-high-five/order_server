package com.nhnacademy.order_server.dto.response;

import com.nhnacademy.order_server.entity.Order;
import com.nhnacademy.order_server.entity.OrderItem;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestOrderDetailResponse {

    private Long orderId;
    private String orderNumber;
    private LocalDateTime orderDate;
    private String statusName;

    private String receiverName;
    private String receiverPhone;
    private String address;
    private String addressDetail;
    private String deliveryRequest;
    private Long wrappingFee;

    private Long totalAmount;
    private Long deliveryFee;
    private Long couponDiscount;
    private Long pointDiscount;
    private Long paymentAmount;

    private List<GuestOrderItemResponse> orderItems;

    public static GuestOrderDetailResponse from(Order order) {
        long calculatedWrappingFee = order.getOrderItems().stream()
                .filter(item -> item.getWrapper() != null) // 포장지 있는 것만 필터링
                .mapToLong(item -> (long) item.getWrapper().getWrapperPrice() * item.getQuantity()) // 가격 * 수량
                .sum();

        List<GuestOrderItemResponse> itemResponses = order.getOrderItems().stream()
                .map(GuestOrderItemResponse::from)
                .collect(Collectors.toList());

        String receiverName = (order.getReceiverName() != null) ? order.getReceiverName() : "";
        String addr = (order.getReceiverAddress() != null) ? order.getReceiverAddress() : "";

        Long deliveryCost = (order.getDeliveryFee() != null) ? Long.valueOf(order.getDeliveryFee()) : 0L;

        return GuestOrderDetailResponse.builder()
                .orderId(order.getId())
                .orderNumber(String.valueOf(order.getId()))
                .orderDate(order.getOrderDate())
                .statusName(order.getDeliveryStatus().toString()) // 한글 변환 필요 시 로직 추가

                .receiverName(receiverName)
                .address(addr)
                .addressDetail("")
                .deliveryRequest("")

                .totalAmount(order.getPaymentAmount() != null ? Long.valueOf(order.getProductAmount()) : 0L)
                .deliveryFee(deliveryCost)
                .wrappingFee(calculatedWrappingFee)
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