package com.nhnacademy.order_server.controller;

import com.nhnacademy.order_server.controller.swagger.OrderReturnControllerDocs;
import com.nhnacademy.order_server.dto.request.OrderReturnRequest;
import com.nhnacademy.order_server.dto.response.OrderReturnCheckResponse;
import com.nhnacademy.order_server.entity.enums.ReturnReason;
import com.nhnacademy.order_server.service.OrderReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderReturnController implements OrderReturnControllerDocs {

    private final OrderReturnService orderReturnService;

    @Override
    @GetMapping("/{orderId}/returns/eligibility")
    public ResponseEntity<OrderReturnCheckResponse> checkReturnEligibility(
            @PathVariable Long orderId,
            @RequestParam(required = false) ReturnReason returnReason) {
        return ResponseEntity.ok(orderReturnService.checkReturnEligibility(orderId, returnReason));
    }

    @Override
    @PostMapping("/{orderId}/returns")
    public ResponseEntity<Void> requestReturn(
            @PathVariable Long orderId,
            @Valid @RequestBody OrderReturnRequest request) {
        orderReturnService.requestReturn(orderId, request);
        return ResponseEntity.ok().build();
    }
}