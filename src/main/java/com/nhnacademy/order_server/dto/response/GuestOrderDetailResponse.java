package com.nhnacademy.order_server.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestOrderDetailResponse {

    // 1. 주문 기본 정보
    private Long orderId;
    private String orderNumber;    // 주문번호
    private LocalDate orderDate;   // 주문일자
    private String statusName;     // 주문상태 (한글)

    // 2. 배송지 정보 (MyOrderResponse에는 이게 없음)
    private String receiverName;
    private String receiverPhone;
    private String address;
    private String addressDetail;
    private String deliveryRequest;

    // 3. 결제 금액 정보
    private Long totalAmount;      // 상품 총액
    private Long deliveryFee;      // 배송비
    private Long couponDiscount;   // 할인액
    private Long pointDiscount;    // 포인트
    private Long paymentAmount;    // 최종 결제액

    // 4. 주문 상품 목록
    private List<GuestOrderItemResponse> orderItems;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GuestOrderItemResponse {
        private String title;
        private Integer quantity;
        private Long price;         // 단가
        private Long totalPrice;    // 총액 (단가 * 수량)
        private String wrapperName; // 포장지 이름 (없으면 null)
    }
}