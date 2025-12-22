package com.nhnacademy.order_server.controller.swagger;

import com.nhnacademy.order_server.dto.request.DeliveryPolicyRequest;
import com.nhnacademy.order_server.dto.response.DeliveryPolicyResponse;
import com.nhnacademy.order_server.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Admin Policy API", description = "관리자 배송비 정책 관리")
@RequestMapping("/api/admin/delivery-policies")
public interface AdminDeliveryPolicyControllerDocs {

    @Operation(summary = "배송 정책 등록", description = "새로운 배송비 정책을 등록하고 즉시 적용합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "정책 등록 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 데이터",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    ResponseEntity<Void> createDeliveryPolicy(@Valid @RequestBody DeliveryPolicyRequest request);

    @Operation(summary = "활성 정책 조회", description = "현재 적용 중인 배송비 정책을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "활성화된 정책이 없음 (DELIVERY_POLICY_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/active")
    ResponseEntity<DeliveryPolicyResponse> getActivePolicy();

    @Operation(summary = "전체 정책 이력 조회", description = "과거 배송비 정책 이력을 모두 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    ResponseEntity<List<DeliveryPolicyResponse>> getAllPolicies();

    @Operation(summary = "배송 정책 삭제", description = "정책을 삭제(비활성화)합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "삭제(비활성화) 성공"),
            @ApiResponse(responseCode = "404", description = "해당 ID의 정책을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{policyId}")
    ResponseEntity<Void> deleteDeliveryPolicy(@PathVariable Long policyId);
}