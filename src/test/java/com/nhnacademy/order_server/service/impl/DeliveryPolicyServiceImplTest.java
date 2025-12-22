package com.nhnacademy.order_server.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.nhnacademy.order_server.dto.request.DeliveryPolicyRequest;
import com.nhnacademy.order_server.dto.response.DeliveryPolicyResponse;
import com.nhnacademy.order_server.entity.DeliveryPolicy;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import com.nhnacademy.order_server.repository.DeliveryPolicyRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DeliveryPolicyServiceImplTest {

    @InjectMocks
    private DeliveryPolicyServiceImpl deliveryPolicyService;

    @Mock
    private DeliveryPolicyRepository deliveryPolicyRepository;

    @Test
    @DisplayName("배송 정책 등록 - 기존 정책 비활성화 및 새 정책 저장")
    void createDeliveryPolicy() {
        DeliveryPolicyRequest request = new DeliveryPolicyRequest();
        ReflectionTestUtils.setField(request, "standardShippingFee", 3000);
        ReflectionTestUtils.setField(request, "minOrderAmount", 30000);

        DeliveryPolicy oldPolicy = new DeliveryPolicy(20000, 2500);
        given(deliveryPolicyRepository.findByIsActiveTrue()).willReturn(Optional.of(oldPolicy));

        deliveryPolicyService.createDeliveryPolicy(request);

        assertThat(oldPolicy.getIsActive()).isFalse();
        verify(deliveryPolicyRepository, times(1)).save(any(DeliveryPolicy.class));
    }

    @Test
    @DisplayName("활성 정책 조회 성공")
    void getActivePolicy_Success() {
        DeliveryPolicy policy = new DeliveryPolicy(50000, 2500);
        ReflectionTestUtils.setField(policy, "id", 1L); // ID 설정
        given(deliveryPolicyRepository.findByIsActiveTrue()).willReturn(Optional.of(policy));

        DeliveryPolicyResponse response = deliveryPolicyService.getActivePolicy();

        assertThat(response.getStandardShippingFee()).isEqualTo(2500);
        assertThat(response.getMinOrderAmount()).isEqualTo(50000);
        assertThat(response.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("활성 정책 조회 실패 - 데이터 없음")
    void getActivePolicy_Fail() {
        given(deliveryPolicyRepository.findByIsActiveTrue()).willReturn(Optional.empty());

        assertThatThrownBy(() -> deliveryPolicyService.getActivePolicy())
                .isInstanceOf(OrderException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.DELIVERY_POLICY_NOT_FOUND);
    }

    @Test
    @DisplayName("정책 삭제(비활성화) 성공")
    void deleteDeliveryPolicy() {
        Long policyId = 1L;
        DeliveryPolicy policy = new DeliveryPolicy(30000, 3000);
        given(deliveryPolicyRepository.findById(policyId)).willReturn(Optional.of(policy));

        deliveryPolicyService.deleteDeliveryPolicy(policyId);

        assertThat(policy.getIsActive()).isFalse();
    }
}