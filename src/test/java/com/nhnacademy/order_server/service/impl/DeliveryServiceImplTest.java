package com.nhnacademy.order_server.service.impl;

import com.nhnacademy.order_server.entity.DeliveryPolicy;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import com.nhnacademy.order_server.service.DeliveryPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceImplTest {

    @InjectMocks
    private DeliveryServiceImpl deliveryService;

    @Mock
    private DeliveryPolicyService deliveryPolicyService;

    private DeliveryPolicy activePolicy;

    @BeforeEach
    void setUp() {
        activePolicy = DeliveryPolicy.builder()
                .minOrderAmount(30000)
                .standardShippingFee(3000)
                .remoteAreaSurcharge(5000)
                .build();
    }

    @Test
    @DisplayName("성공: 기본 배송비 부과 (기준 금액 미만, 일반 지역)")
    void calculateDeliveryFee_Standard() {
        // Given
        when(deliveryPolicyService.getActivePolicyEntity()).thenReturn(activePolicy);
        int productAmount = 10000; // 3만원 미만
        String address = "서울시 강남구";

        // When
        int fee = deliveryService.calculateDeliveryFee(productAmount, address);

        // Then
        assertThat(fee).isEqualTo(3000);
    }

    @Test
    @DisplayName("성공: 무료 배송 (기준 금액 이상, 일반 지역)")
    void calculateDeliveryFee_Free() {
        // Given
        when(deliveryPolicyService.getActivePolicyEntity()).thenReturn(activePolicy);
        int productAmount = 50000; // 3만원 이상
        String address = "부산시 해운대구";

        // When
        int fee = deliveryService.calculateDeliveryFee(productAmount, address);

        // Then
        assertThat(fee).isEqualTo(0);
    }

    @Test
    @DisplayName("성공: 도서산간 추가 요금 부과 (기준 금액 미만, 제주도)")
    void calculateDeliveryFee_Remote_Jeju() {
        // Given
        when(deliveryPolicyService.getActivePolicyEntity()).thenReturn(activePolicy);
        int productAmount = 10000;
        String address = "제주특별자치도 제주시"; // '제주' 포함

        // When
        int fee = deliveryService.calculateDeliveryFee(productAmount, address);

        // Then: 3000(기본) + 5000(추가) = 8000
        assertThat(fee).isEqualTo(8000);
    }

    @Test
    @DisplayName("성공: 도서산간 무료 배송이지만 추가 요금은 부과 (기준 금액 이상, 도서 지역)")
    void calculateDeliveryFee_Free_But_Remote() {
        // Given
        when(deliveryPolicyService.getActivePolicyEntity()).thenReturn(activePolicy);
        int productAmount = 50000; // 무료 배송 대상
        String address = "경상북도 울릉군 울릉읍 도서지역"; // '도서' 포함

        // When
        int fee = deliveryService.calculateDeliveryFee(productAmount, address);

        // Then: 0(기본무료) + 5000(추가) = 5000
        assertThat(fee).isEqualTo(5000);
    }

    @Test
    @DisplayName("성공: 정책에 도서산간 비용이 없을 경우(null) 기본값(5000) 적용")
    void calculateDeliveryFee_Remote_Default_Surcharge() {
        // Given
        DeliveryPolicy policyWithoutSurcharge = DeliveryPolicy.builder()
                .minOrderAmount(30000)
                .standardShippingFee(3000)
                .remoteAreaSurcharge(null) // 정책값 null 설정
                .build();

        when(deliveryPolicyService.getActivePolicyEntity()).thenReturn(policyWithoutSurcharge);
        int productAmount = 10000;
        String address = "제주도 서귀포시";

        // When
        int fee = deliveryService.calculateDeliveryFee(productAmount, address);

        // Then: 3000 + 5000(DEFAULT) = 8000
        assertThat(fee).isEqualTo(8000);
    }

    @Test
    @DisplayName("실패: 주문 금액이 null인 경우 예외 발생")
    void calculateDeliveryFee_Fail_NullAmount() {
        // Given
        Integer productAmount = null;
        String address = "서울시";

        // When & Then
        OrderException ex = assertThrows(OrderException.class,
                () -> deliveryService.calculateDeliveryFee(productAmount, address));
        assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("실패: 주문 금액이 음수인 경우 예외 발생")
    void calculateDeliveryFee_Fail_NegativeAmount() {
        // Given
        int productAmount = -1000;
        String address = "서울시";

        // When & Then
        OrderException ex = assertThrows(OrderException.class,
                () -> deliveryService.calculateDeliveryFee(productAmount, address));
        assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("성공: 주소가 null이어도 에러 없이 일반 지역으로 처리")
    void calculateDeliveryFee_NullAddress() {
        // Given
        when(deliveryPolicyService.getActivePolicyEntity()).thenReturn(activePolicy);
        int productAmount = 10000;
        String address = null; // 주소 없음

        // When
        int fee = deliveryService.calculateDeliveryFee(productAmount, address);

        // Then: 기본 배송비만 부과 (3000)
        assertThat(fee).isEqualTo(3000);
    }
}