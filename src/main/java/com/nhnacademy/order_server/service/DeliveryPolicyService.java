package com.nhnacademy.order_server.service;

import com.nhnacademy.order_server.dto.request.DeliveryPolicyRequest;
import com.nhnacademy.order_server.dto.response.DeliveryPolicyResponse;
import com.nhnacademy.order_server.entity.DeliveryPolicy;
import java.util.List;

public interface DeliveryPolicyService {

    void createDeliveryPolicy(DeliveryPolicyRequest request);
    DeliveryPolicyResponse getActivePolicy();
    List<DeliveryPolicyResponse> getAllPolicies();
    void deleteDeliveryPolicy(Long policyId);
    DeliveryPolicy getActivePolicyEntity();
}
