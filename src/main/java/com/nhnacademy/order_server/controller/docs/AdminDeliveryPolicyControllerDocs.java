package com.nhnacademy.order_server.controller.docs;

import com.nhnacademy.order_server.dto.request.DeliveryPolicyRequest;
import com.nhnacademy.order_server.dto.response.DeliveryPolicyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin Policy API", description = "관리자 배송비 정책 관리")
@RequestMapping("/api/admin/delivery-policies")
public interface AdminDeliveryPolicyControllerDocs {

    @Operation(summary = "배송 정책 등록", description = "새로운 배송비 정책을 등록하고 즉시 적용합니다.")
    @PostMapping
    ResponseEntity<Void> createDeliveryPolicy(@Valid @RequestBody DeliveryPolicyRequest request);

    @Operation(summary = "활성 정책 조회", description = "현재 적용 중인 배송비 정책을 조회합니다.")
    @GetMapping("/active")
    ResponseEntity<DeliveryPolicyResponse> getActivePolicy();

    @Operation(summary = "전체 정책 이력 조회", description = "과거 배송비 정책 이력을 모두 조회합니다.")
    @GetMapping
    ResponseEntity<List<DeliveryPolicyResponse>> getAllPolicies();

    @Operation(summary = "배송 정책 삭제", description = "정책을 삭제(비활성화)합니다.")
    @DeleteMapping("/{policyId}")
    ResponseEntity<Void> deleteDeliveryPolicy(@PathVariable Integer policyId);
}