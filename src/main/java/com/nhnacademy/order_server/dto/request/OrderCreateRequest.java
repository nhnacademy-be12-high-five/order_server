package com.nhnacademy.order_server.dto.request;

import com.nhnacademy.order_server.entity.Order;
import com.nhnacademy.order_server.entity.OrderItem;
import com.nhnacademy.order_server.entity.Wrapper;
import com.nhnacademy.order_server.entity.enums.DeliveryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "주문 생성 요청")
public class OrderCreateRequest {

    @Schema(description = "회원 ID (비회원은 null)", example = "100")
    private Long userId;

    @Schema(description = "비회원 주문 비밀번호 (회원은 null)", example = "1234")
    private String orderPassword;

    @NotBlank
    @Schema(description = "수령자 이름", example = "홍길동")
    private String receiverName;

    @NotBlank
    @Schema(description = "배송 주소", example = "광주광역시 ...")
    private String receiverAddress;

    @Schema(description = "희망 배송일 (없으면 가장 빠른 날짜)", example = "2025-12-25")
    private LocalDate requestDeliveryDate;

    @Schema(description = "사용할 쿠폰 ID (선택)", example = "10")
    private Long couponId;

    @Schema(description = "사용할 포인트 금액 (선택)", example = "3000")
    @Min(0)
    private Integer usedPoint;

    @Valid
    @NotNull
    @Schema(description = "주문 상품 목록")
    private List<OrderItemRequest> orderItems;

    @Getter
    @NoArgsConstructor
    public static class OrderItemRequest {
        @NotNull
        private Long bookId;

        @NotNull
        @Min(1)
        private Integer quantity;

        @Schema(description = "선택한 포장지 ID (없으면 null)")
        private Long wrapperId;

        public OrderItem toEntity(int bookPrice, String bookTitle, Wrapper wrapper) {
            return OrderItem.builder()
                    .bookId(this.bookId)
                    .bookTitle(bookTitle)
                    .quantity(this.quantity)
                    .unitPrice(bookPrice)
                    .wrapper(wrapper)
                    .isWrapped(wrapper != null)
                    .key(UUID.randomUUID().toString())
                    .build();
        }
    }

    @Builder
    @Getter
    public static class OrderCalculationResult {
        private int productAmount;
        private int deliveryFee;
        private int wrappingFee;
        private int couponDiscount;
        private int pointDiscount;
        private int paymentAmount;
        private int earnedPoint;
    }

    public Order toEntity(OrderCalculationResult calculation, String orderKey, String encryptedPassword) {
        return Order.builder()
                .userId(this.userId)
                .isMember(this.userId != null)
                .receiverName(this.receiverName)
                .receiverAddress(this.receiverAddress)
                .orderDate(LocalDateTime.now())

                .deliveryStatus(DeliveryStatus.PAYMENT_WAITING)

                .productAmount(calculation.getProductAmount())
                .deliveryFee(calculation.getDeliveryFee())
                .wrappingFee(calculation.getWrappingFee())
                .couponDiscount(calculation.getCouponDiscount())
                .pointDiscount(calculation.getPointDiscount())
                .paymentAmount(calculation.getPaymentAmount())
                .earnedPoint(calculation.getEarnedPoint())

                .orderKey(orderKey)
                .orderPassword(encryptedPassword)
                .build();
    }
}