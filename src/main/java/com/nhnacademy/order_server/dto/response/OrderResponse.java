package com.nhnacademy.order_server.dto.response;

import com.nhnacademy.order_server.entity.Order;
import com.nhnacademy.order_server.entity.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
@Schema(description = "주문 정보 응답")
public class OrderResponse {

    @Schema(description = "주문 번호")
    private Long orderId;

    @Schema(description = "주문 일시")
    private LocalDateTime orderDate;

    @Schema(description = "주문 상태 (WAITING, COMPLETED 등)")
    private String status;

    @Schema(description = "총 결제 금액")
    private Integer totalAmount;

    @Schema(description = "주문 상품 목록")
    private List<OrderItemResponse> items;

    public static OrderResponse from(Order order) {
        return OrderResponse.builder()
                .orderId(order.getId())
                .orderDate(order.getOrderDate())
                .status(order.getDeliveryStatus().name()) // Enum -> String 변환
                .totalAmount(order.getPaymentAmount())
                .items(order.getOrderItems().stream()
                        .map(OrderItemResponse::from) // 아래의 from 메서드 호출
                        .collect(Collectors.toList()))
                .build();
    }

    @Getter
    @Builder
    @Schema(description = "주문 상품 상세 정보")
    public static class OrderItemResponse {

        @Schema(description = "책 제목")
        private String bookTitle;

        @Schema(description = "주문 수량")
        private Integer quantity;

        @Schema(description = "구매 당시 가격 (단가)")
        private Integer price;

        public static OrderItemResponse from(OrderItem orderItem) {
            return OrderItemResponse.builder()
                    // [핵심] 주문 생성 시점에 저장해둔 책 제목을 바로 사용 (N+1 문제 해결)
                    // (주의: OrderItem 엔티티에 getBookTitle() Getter가 있어야 함)
                    .bookTitle(orderItem.getBookTitle())
                    .quantity(orderItem.getQuantity())
                    .price(orderItem.getUnitPrice())
                    .build();
        }
    }
}