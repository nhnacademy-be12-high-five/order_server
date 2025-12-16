package com.nhnacademy.order_server.service.impl;

import com.nhnacademy.order_server.dto.response.DeliveryPolicyResponse;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import com.nhnacademy.order_server.service.DeliveryPolicyService;
import com.nhnacademy.order_server.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryServiceImpl implements DeliveryService {

    private static final int DEFAULT_REMOTE_AREA_SURCHARGE = 5000;
    private final DeliveryPolicyService deliveryPolicyService;

    @Override
    public int calculateDeliveryFee(Integer productAmount, String address) {

        if (productAmount == null || productAmount < 0) {
            throw new OrderException(OrderErrorCode.INVALID_REQUEST);
        }

        DeliveryPolicyResponse policy;

        // [수정] 정책 조회 시 예외가 발생하면 null로 처리하여 기본값 로직으로 넘기도록 수정
        try {
            policy = deliveryPolicyService.getActivePolicy();
        } catch (OrderException e) {
            // 정책을 찾을 수 없는 경우(DELIVERY_POLICY_NOT_FOUND)에는 policy를 null로 간주
            if (e.getErrorCode() == OrderErrorCode.DELIVERY_POLICY_NOT_FOUND) {
                policy = null;
            } else {
                // 그 외 다른 에러라면 상위로 전파
                throw e;
            }
        }

        // 2. 정책이 없을 경우(null) 기본값 적용
        if (policy == null) {
            log.warn("활성 배송 정책을 찾을 수 없어 기본 정책을 적용합니다.");
            policy = DeliveryPolicyResponse.builder()
                    .standardShippingFee(3000)   // 기본 배송비 3,000원
                    .minOrderAmount(30000)      // 무료 배송 기준 30,000원 (명시적 L 접미사 권장)
                    .remoteAreaSurcharge(5000)   // 도서산간 추가비 5,000원
                    .build();
        }

        int deliveryFee = 0;
        int safeProductAmount = productAmount;

        // 3. 무료 배송 기준 확인 (Null Safe 처리)
        long minOrderAmount = (policy.getMinOrderAmount() != null) ? policy.getMinOrderAmount() : 30000L;
        int standardFee = (policy.getStandardShippingFee() != null) ? policy.getStandardShippingFee() : 3000;

        if (safeProductAmount < minOrderAmount) {
            deliveryFee = standardFee;
        }

        if (isRemoteArea(address)) {
            Integer policySurcharge = policy.getRemoteAreaSurcharge();
            int surcharge = (policySurcharge != null) ? policySurcharge : DEFAULT_REMOTE_AREA_SURCHARGE;
            deliveryFee += surcharge;
        }

        return deliveryFee;
    }

    private boolean isRemoteArea(String address) {
        return address != null && (address.contains("제주") || address.contains("도서"));
    }
}