package com.nhnacademy.order_server.service.impl;

import com.nhnacademy.order_server.dto.request.DeliveryPolicyRequest;
import com.nhnacademy.order_server.dto.response.DeliveryPolicyResponse;
import com.nhnacademy.order_server.entity.DeliveryPolicy;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import com.nhnacademy.order_server.repository.DeliveryPolicyRepository;
import com.nhnacademy.order_server.service.DeliveryPolicyService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryPolicyServiceImpl implements DeliveryPolicyService {

    private final DeliveryPolicyRepository deliveryPolicyRepository;

    @Override
    @Transactional
    // [추가] 정책이 새로 생성되면 기존 캐시('activePolicy')를 삭제해야 함
    @CacheEvict(value = "activeDeliveryPolicy", allEntries = true)
    public void createDeliveryPolicy(DeliveryPolicyRequest request) {
        deliveryPolicyRepository.findByIsActiveTrue()
                .ifPresent(DeliveryPolicy::deactivate);

        deliveryPolicyRepository.save(request.toEntity());
    }

    @Override
    @Cacheable(value = "activeDeliveryPolicy", key = "'activePolicy'")
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
    @CacheEvict(value = "activeDeliveryPolicy", allEntries = true)
    public void deleteDeliveryPolicy(Long policyId) {
        DeliveryPolicy policy = deliveryPolicyRepository.findById(policyId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.DELIVERY_POLICY_NOT_FOUND));

        policy.deactivate();
    }

    @Override

    public DeliveryPolicy getActivePolicyEntity() {
        return deliveryPolicyRepository.findByIsActiveTrue()
                .orElseThrow(() -> new OrderException(OrderErrorCode.DELIVERY_POLICY_NOT_FOUND));
    }
}