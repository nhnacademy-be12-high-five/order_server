package com.nhnacademy.order_server.service.impl;

import com.nhnacademy.order_server.dto.request.DeliveryPolicyRequest;
import com.nhnacademy.order_server.dto.response.DeliveryPolicyResponse;
import com.nhnacademy.order_server.entity.DeliveryPolicy;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import com.nhnacademy.order_server.repository.DeliveryPolicyRepository;
import com.nhnacademy.order_server.service.DeliveryPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryPolicyServiceImpl implements DeliveryPolicyService {

    private final DeliveryPolicyRepository deliveryPolicyRepository;

    @Override
    @Transactional
    public void createDeliveryPolicy(DeliveryPolicyRequest request) {
        deliveryPolicyRepository.findByIsActiveTrue()
                .ifPresent(DeliveryPolicy::deactivate);

        deliveryPolicyRepository.save(request.toEntity());
    }

    @Override
    public DeliveryPolicyResponse getActivePolicy() {
        DeliveryPolicy policy = deliveryPolicyRepository.findByIsActiveTrue()
                .orElseThrow(() -> new OrderException(OrderErrorCode.DELIVERY_POLICY_NOT_FOUND));
        return DeliveryPolicyResponse.from(policy);
    }

    @Override
    public List<DeliveryPolicyResponse> getAllPolicies() {
        return deliveryPolicyRepository.findAll().stream()
                .map(DeliveryPolicyResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteDeliveryPolicy(Long policyId) {
        DeliveryPolicy policy = deliveryPolicyRepository.findById(policyId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.DELIVERY_POLICY_NOT_FOUND));

        policy.deactivate();
    }

    @Override
    @Cacheable(value = "activeDeliveryPolicy", key = "'activePolicy'")
    public DeliveryPolicy getActivePolicyEntity() {
        return deliveryPolicyRepository.findByIsActiveTrue()
                .orElseThrow(() -> new OrderException(OrderErrorCode.DELIVERY_POLICY_NOT_FOUND));
    }
}