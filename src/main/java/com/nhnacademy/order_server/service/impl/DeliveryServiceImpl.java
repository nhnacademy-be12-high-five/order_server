package com.nhnacademy.order_server.service.impl;

import com.nhnacademy.order_server.entity.DeliveryPolicy;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import com.nhnacademy.order_server.repository.DeliveryPolicyRepository;
import com.nhnacademy.order_server.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryServiceImpl implements DeliveryService {

    private final DeliveryPolicyRepository deliveryPolicyRepository;

    public int calculateDeliveryFee(Integer productAmount, String address){

        DeliveryPolicy policy = deliveryPolicyRepository.findByIsActiveTrue()
                .orElseThrow(()-> new OrderException(OrderErrorCode.DELIVERY_POLICY_NOT_FOUND));

        int deliveryFee = 0;
        if(productAmount < policy.getMinOrderAmount()){
            deliveryFee = policy.getStandardShippingFee();
        }

        if (isRemoteArea(address)){
            deliveryFee += 5000;
        }

        return deliveryFee;
    }

    private boolean isRemoteArea(String address){
        return address != null && (address.contains("제주") || address.contains("도서"));
    }
}
