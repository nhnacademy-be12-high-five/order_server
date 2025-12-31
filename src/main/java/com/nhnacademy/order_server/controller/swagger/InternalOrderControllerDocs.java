package com.nhnacademy.order_server.controller.swagger;

import com.nhnacademy.order_server.dto.response.OrderAggregationDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Internal Order API", description = "서버 간 통신을 위한 주문 데이터 집계 및 조회 API")
public interface InternalOrderControllerDocs {

    @Operation(summary = "전 회원 대상 주문 집계 조회",
            description = "특정 기간 동안 구매 확정된 모든 회원의 ID와 순수 결제 금액 합계를 조회합니다. (등급 산정 배치용)")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    ResponseEntity<List<OrderAggregationDto>> getOrderAggregations(
            @Parameter(description = "시작 일시 (ISO DATE_TIME 형식)", example = "2023-12-01T00:00:00")
            @RequestParam LocalDateTime startDate,
            @Parameter(description = "종료 일시 (ISO DATE_TIME 형식)", example = "2023-12-31T23:59:59")
            @RequestParam LocalDateTime endDate);

    @Operation(summary = "특정 회원의 순수 결제 총액 조회",
            description = "특정 회원이 기준일 이후 구매 확정한 주문의 총 결제 금액을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    ResponseEntity<Long> getTotalAmount(
            @Parameter(description = "회원 ID", example = "1")
            @PathVariable Long userId,
            @Parameter(description = "조회 기준 시작 일시", example = "2023-10-01T00:00:00")
            @RequestParam LocalDateTime since);
}