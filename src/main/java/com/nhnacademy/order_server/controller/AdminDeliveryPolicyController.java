package com.nhnacademy.order_server.controller;

import com.nhnacademy.order_server.controller.docs.AdminDeliveryPolicyControllerDocs;
import com.nhnacademy.order_server.dto.request.DeliveryPolicyRequest;
import com.nhnacademy.order_server.dto.response.DeliveryPolicyResponse;
import com.nhnacademy.order_server.service.DeliveryPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AdminDeliveryPolicyController implements AdminDeliveryPolicyControllerDocs {

    private final DeliveryPolicyService deliveryPolicyService;

    @Override
    public ResponseEntity<Void> createDeliveryPolicy(DeliveryPolicyRequest request) {
        deliveryPolicyService.createDeliveryPolicy(request);
        return ResponseEntity.status(201).build();
    }

    @Override
    public ResponseEntity<DeliveryPolicyResponse> getActivePolicy() {
        return ResponseEntity.ok(deliveryPolicyService.getActivePolicy());
    }

    @Override
    public ResponseEntity<List<DeliveryPolicyResponse>> getAllPolicies() {
        return ResponseEntity.ok(deliveryPolicyService.getAllPolicies());
    }

    @Override
    public ResponseEntity<Void> deleteDeliveryPolicy(Long policyId) {
        deliveryPolicyService.deleteDeliveryPolicy(policyId);
        return ResponseEntity.noContent().build();
    }
}