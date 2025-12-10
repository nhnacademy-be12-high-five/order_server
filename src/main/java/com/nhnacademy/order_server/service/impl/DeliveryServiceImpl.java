package com.nhnacademy.order_server.service.impl;

import com.nhnacademy.order_server.entity.DeliveryPolicy;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import com.nhnacademy.order_server.service.DeliveryPolicyService;
import com.nhnacademy.order_server.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryServiceImpl implements DeliveryService {

    private static final int DEFAULT_REMOTE_AREA_SURCHARGE = 5000;

    private final DeliveryPolicyService deliveryPolicyService;

    public int calculateDeliveryFee(Integer productAmount, String address){

        if (productAmount == null || productAmount < 0) {
                       throw new OrderException(OrderErrorCode.INVALID_REQUEST);
        }

        int safeProductAmount = productAmount;

        DeliveryPolicy policy = deliveryPolicyService.getActivePolicyEntity();

        int deliveryFee = 0;

        if(safeProductAmount < policy.getMinOrderAmount()){
            deliveryFee = policy.getStandardShippingFee();
        }

        if (isRemoteArea(address)){
            int surcharge = (policy.getRemoteAreaSurcharge() != null)
                    ? policy.getRemoteAreaSurcharge()
                    : DEFAULT_REMOTE_AREA_SURCHARGE;
            deliveryFee += surcharge;
        }

        return deliveryFee;
    }

    private boolean isRemoteArea(String address){
        return address != null && (address.contains("제주") || address.contains("도서"));
    }
}