package com.nhnacademy.order_server.dto.response;

import com.nhnacademy.order_server.entity.Order;
import com.nhnacademy.order_server.entity.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "주문 정보 응답")
public class OrderResponse {

    @Schema(description = "주문 번호")
    private Long id; // 프론트엔드와 맞춘 필드명 (orderId -> id)

    @Schema(description = "주문자 ID")
    private Long userId;

    @Schema(description = "주문명 (예: 책 제목 외 1건)")
    private String orderName;

    @Schema(description = "주문 일시")
    private LocalDateTime orderDate;

    @Schema(description = "주문 상태")
    private String status;

    @Schema(description = "총 결제 금액")
    private Integer totalPrice; // 프론트엔드와 맞춘 필드명 (totalAmount -> totalPrice)

    @Schema(description = "송장 번호")
    private String trackingNumber;

    @Schema(description = "주문 상품 목록")
    private List<OrderItemResponse> items;

    // ==========================================
    // [핵심 수정] 엔티티 -> DTO 변환 로직 보강
    // ==========================================
    public static OrderResponse from(Order order) {

        // 1. 주문 상품 목록 변환
        List<OrderItemResponse> itemResponses = order.getOrderItems() != null
                ? order.getOrderItems().stream()
                .map(OrderItemResponse::from)
                .toList()
                : Collections.emptyList();

        // 2. 주문명(orderName) 생성 로직: "첫 번째 책 제목 외 N건"
        String generatedOrderName = "상품 정보 없음";
        if (!itemResponses.isEmpty()) {
            String firstBookTitle = itemResponses.get(0).getBookTitle();
            if (itemResponses.size() > 1) {
                generatedOrderName = firstBookTitle + " 외 " + (itemResponses.size() - 1) + "건";
            } else {
                generatedOrderName = firstBookTitle;
            }
        }

        // 3. 송장 번호(trackingNumber) 추출 (Null 체크 필수)
        String trackingNum = "";
        if (order.getDelivery() != null) {
            trackingNum = order.getDelivery().getTrackingNumber();
        }

        // 4. 최종 빌더 반환
        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())          // [추가] 이게 없어서 "비회원"으로 뜸
                .orderName(generatedOrderName)      // [추가] 이게 없어서 상품명이 안 나옴
                .orderDate(order.getOrderDate())
                .status(order.getDeliveryStatus() != null ? order.getDeliveryStatus().name() : "UNKNOWN")
                .totalPrice(order.getPaymentAmount()) // 필드명 주의 (totalPrice)
                .trackingNumber(trackingNum)        // [추가] 송장번호 매핑
                .items(itemResponses)
                .build();
    }

    @Getter
    @Builder
    @Schema(description = "주문 상품 상세 정보")
    public static class OrderItemResponse {
        private String bookTitle;
        private Integer quantity;
        private Integer price;

        public static OrderItemResponse from(OrderItem orderItem) {
            return OrderItemResponse.builder()
                    .bookTitle(orderItem.getBookTitle())
                    .quantity(orderItem.getQuantity())
                    .price(orderItem.getUnitPrice())
                    .build();
        }
    }
}