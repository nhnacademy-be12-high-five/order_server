package com.nhnacademy.order_server.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nhnacademy.order_server.entity.DeliveryPolicy;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import com.nhnacademy.order_server.service.DeliveryPolicyService;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceImplTest {

    @InjectMocks
    private DeliveryServiceImpl deliveryService;

    @Mock
    private DeliveryPolicyService deliveryPolicyService;

    @BeforeEach
    void setUp() {
        DeliveryPolicy.builder()
                .minOrderAmount(30000)
                .standardShippingFee(3000)
                .remoteAreaSurcharge(5000)
                .build();

        DeliveryPolicy.builder()
                .minOrderAmount(30000)
                .standardShippingFee(3000)
                .remoteAreaSurcharge(null)
                .build();
    }

    // =========================
    // 성공 케이스 (Parameterized)
    // =========================
    @ParameterizedTest(name = "[{index}] amount={0}, address={1} → fee={2}")
    @MethodSource("deliveryFeeSuccessCases")
    @DisplayName("성공: 배송비 계산")
    void calculateDeliveryFee_Success(
            Integer productAmount,
            String address,
            int expectedFee
    ) {
        // when
        int fee = deliveryService.calculateDeliveryFee(productAmount, address);

        // then
        assertThat(fee).isEqualTo(expectedFee);
    }

    static Stream<Arguments> deliveryFeeSuccessCases() {
        DeliveryPolicy defaultPolicy = DeliveryPolicy.builder()
                .minOrderAmount(30000)
                .standardShippingFee(3000)
                .remoteAreaSurcharge(5000)
                .build();

        DeliveryPolicy noSurchargePolicy = DeliveryPolicy.builder()
                .minOrderAmount(30000)
                .standardShippingFee(3000)
                .remoteAreaSurcharge(null)
                .build();

        return Stream.of(
                // 기준 금액 미만, 일반 지역
                Arguments.of(10000, "서울시 강남구", 3000, defaultPolicy),

                // 기준 금액 이상, 일반 지역
                Arguments.of(50000, "부산시 해운대구", 0, defaultPolicy),

                // 경계값 (기준 금액과 동일)
                Arguments.of(30000, "서울시 강남구", 0, defaultPolicy),

                // 기준 금액 미만, 도서산간 (제주)
                Arguments.of(10000, "제주특별자치도 제주시", 8000, defaultPolicy),

                // 기준 금액 이상, 도서산간
                Arguments.of(50000, "경상북도 울릉군 울릉읍 도서지역", 5000, defaultPolicy),

                // 도서산간 추가 요금 정책값 null → 기본값 5000
                Arguments.of(10000, "제주도 서귀포시", 8000, noSurchargePolicy),

                // 주소 null → 일반 지역 처리
                Arguments.of(10000, null, 3000, defaultPolicy)
        );
    }

    // =========================
    // 실패 케이스 (개별 테스트 유지)
    // =========================
    @Test
    @DisplayName("실패: 주문 금액이 null인 경우 예외 발생")
    void calculateDeliveryFee_Fail_NullAmount() {
        // given
        Integer productAmount = null;
        String address = "서울시";

        // when & then
        OrderException ex = assertThrows(OrderException.class,
                () -> deliveryService.calculateDeliveryFee(productAmount, address));

        assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("실패: 주문 금액이 음수인 경우 예외 발생")
    void calculateDeliveryFee_Fail_NegativeAmount() {
        // given
        int productAmount = -1000;
        String address = "서울시";

        // when & then
        OrderException ex = assertThrows(OrderException.class,
                () -> deliveryService.calculateDeliveryFee(productAmount, address));

        assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.INVALID_REQUEST);
    }
}
