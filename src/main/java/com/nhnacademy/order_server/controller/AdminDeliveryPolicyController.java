package com.nhnacademy.order_server.controller;

import com.nhnacademy.order_server.controller.docs.AdminDeliveryPolicyControllerDocs;
import com.nhnacademy.order_server.dto.request.DeliveryPolicyRequest;
import com.nhnacademy.order_server.dto.response.DeliveryPolicyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class AdminDeliveryPolicyController implements AdminDeliveryPolicyControllerDocs {

    // private final DeliveryPolicyService deliveryPolicyService; // 나중에 주입

    @Override
    public ResponseEntity<Void> createDeliveryPolicy(DeliveryPolicyRequest request) {
        return ResponseEntity.status(201).build();
    }

    @Override
    public ResponseEntity<DeliveryPolicyResponse> getActivePolicy() {
        // 임시 응답 (스웨거 테스트용)
        return ResponseEntity.ok(DeliveryPolicyResponse.builder()
                .id(1)
                .standardShippingFee(3000)
                .minOrderAmount(50000)
                .isActive(true)
                .effectiveDate(LocalDateTime.now())
                .build());
    }

    @Override
    public ResponseEntity<List<DeliveryPolicyResponse>> getAllPolicies() {
        return ResponseEntity.ok(Collections.emptyList());
    }

    @Override
    public ResponseEntity<Void> deleteDeliveryPolicy(Integer policyId) {
        return ResponseEntity.noContent().build();
    }
}