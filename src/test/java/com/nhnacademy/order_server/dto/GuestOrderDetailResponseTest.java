package com.nhnacademy.order_server.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.nhnacademy.order_server.dto.response.GuestOrderDetailResponse;
import com.nhnacademy.order_server.entity.Order;
import com.nhnacademy.order_server.entity.OrderItem;
import com.nhnacademy.order_server.entity.Wrapper;
import com.nhnacademy.order_server.entity.enums.DeliveryStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GuestOrderDetailResponseTest {

    @Test
    @DisplayName("정상 변환: 모든 필드가 채워져 있을 때 (포장비 계산 포함)")
    void from_FullData() {
        // Given
        // 1. 포장지 생성
        Wrapper redWrapper = Wrapper.builder().wrapperName("Red").wrapperPrice(1000).build();

        // 2. 주문 상품 생성 (하나는 포장 있음, 하나는 포장 없음)
        OrderItem item1 = OrderItem.builder()
                .bookTitle("JPA 책")
                .quantity(2)
                .unitPrice(20000)
                .wrapper(redWrapper) // 포장비: 1000 * 2 = 2000
                .build();

        OrderItem item2 = OrderItem.builder()
                .bookTitle("Spring 책")
                .quantity(1)
                .unitPrice(30000)
                .wrapper(null) // 포장 없음
                .build();

        // 3. 주문 생성
        Order order = Order.builder()
                .id(1L)
                .orderDate(LocalDateTime.now())
                .deliveryStatus(DeliveryStatus.DELIVERY_COMPLETED)
                .receiverName("홍길동")
                .receiverAddress("서울시 강남구")
                .deliveryFee(3000) // Integer 타입 가정
                .productAmount(70000) // (20000*2) + 30000
                .couponDiscount(5000)
                .pointDiscount(1000)
                .paymentAmount(67000) // 70000 + 3000 + 2000(포장) - 6000
                .build();

        // OrderItem을 Order에 연결 (Set/List 구조에 따라 다를 수 있으나 Builder나 Setter 사용 가정)
        // 테스트를 위해 강제로 리스트 주입 (Entity 구조에 따라 적절히 변경 필요, 여기선 Reflection or Builder)
        // 제공된 코드상 order.getOrderItems()를 호출하므로, Builder에 포함되어 있다고 가정하거나 설정
        org.springframework.test.util.ReflectionTestUtils.setField(order, "orderItems", List.of(item1, item2));

        // When
        GuestOrderDetailResponse response = GuestOrderDetailResponse.from(order);

        // Then
        // 1. 기본 필드 매핑 확인
        assertThat(response.getOrderId()).isEqualTo(1L);
        assertThat(response.getOrderNumber()).isEqualTo("1");
        assertThat(response.getStatusName()).isEqualTo("DELIVERY_COMPLETED");
        assertThat(response.getReceiverName()).isEqualTo("홍길동");
        assertThat(response.getAddress()).isEqualTo("서울시 강남구");

        // 2. 계산 로직 확인 (포장비)
        // item1 (1000원 * 2개) + item2 (없음) = 2000원
        assertThat(response.getWrappingFee()).isEqualTo(2000L);

        // 3. 금액 필드 확인
        assertThat(response.getDeliveryFee()).isEqualTo(3000L);
        assertThat(response.getCouponDiscount()).isEqualTo(5000L);
        assertThat(response.getPointDiscount()).isEqualTo(1000L);
        assertThat(response.getTotalAmount()).isEqualTo(70000L);

        // 4. 내부 리스트(GuestOrderItemResponse) 변환 확인
        assertThat(response.getOrderItems()).hasSize(2);
        assertThat(response.getOrderItems().get(0).getWrapperName()).isEqualTo("Red");
        assertThat(response.getOrderItems().get(0).getTotalPrice()).isEqualTo(40000L); // 20000 * 2
        assertThat(response.getOrderItems().get(1).getWrapperName()).isNull();
    }

    @Test
    @DisplayName("Null 처리: 필수 값이 없을 때 기본값(0 또는 빈 문자열)으로 변환되는지 확인")
    void from_NullValues() {
        // Given
        OrderItem item = OrderItem.builder()
                .bookTitle("Test Book")
                .quantity(1)
                .unitPrice(10000)
                .build(); // Wrapper Null

        Order order = Order.builder()
                .id(2L)
                .orderDate(LocalDateTime.now())
                .deliveryStatus(DeliveryStatus.PAYMENT_WAITING)
                // 아래 필드들을 명시적으로 null로 설정 (Builder 기본값이 null이라 가정)
                .receiverName(null)
                .receiverAddress(null)
                .deliveryFee(null)
                .couponDiscount(null)
                .pointDiscount(null)
                .paymentAmount(null)
                .productAmount(0)
                .build();

        org.springframework.test.util.ReflectionTestUtils.setField(order, "orderItems", List.of(item));

        // When
        GuestOrderDetailResponse response = GuestOrderDetailResponse.from(order);

        // Then
        // 삼항 연산자 (? "" : value) 테스트
        assertThat(response.getReceiverName()).isEmpty();
        assertThat(response.getAddress()).isEmpty();

        // 삼항 연산자 (? 0L : value) 테스트
        assertThat(response.getDeliveryFee()).isZero();
        assertThat(response.getCouponDiscount()).isZero();
        assertThat(response.getPointDiscount()).isZero();
        assertThat(response.getPaymentAmount()).isZero();

        // 포장비 0원 확인
        assertThat(response.getWrappingFee()).isZero();
    }

    @Test
    @DisplayName("Inner Class: GuestOrderItemResponse 변환 테스트")
    void guestOrderItemResponse_from() {
        // Given
        Wrapper wrapper = Wrapper.builder().wrapperName("Blue").build();
        OrderItem item = OrderItem.builder()
                .bookTitle("책")
                .quantity(3)
                .unitPrice(1000)
                .wrapper(wrapper)
                .build();

        // When
        GuestOrderDetailResponse.GuestOrderItemResponse response =
                GuestOrderDetailResponse.GuestOrderItemResponse.from(item);

        // Then
        assertThat(response.getTitle()).isEqualTo("책");
        assertThat(response.getQuantity()).isEqualTo(3);
        assertThat(response.getPrice()).isEqualTo(1000L);
        assertThat(response.getTotalPrice()).isEqualTo(3000L); // 1000 * 3
        assertThat(response.getWrapperName()).isEqualTo("Blue");
    }
}