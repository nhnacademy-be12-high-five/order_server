package com.nhnacademy.order_server.controller;

import com.nhnacademy.order_server.controller.swagger.InternalOrderControllerDocs; // 추가
import com.nhnacademy.order_server.dto.response.OrderAggregationDto;
import com.nhnacademy.order_server.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/internal/orders")
@RequiredArgsConstructor
public class InternalOrderController implements InternalOrderControllerDocs {

    private final OrderService orderService;

    @Override
    @GetMapping("/aggregations")
    public ResponseEntity<List<OrderAggregationDto>> getOrderAggregations(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        return ResponseEntity.ok(orderService.getOrderAggregations(startDate, endDate));
    }

    @Override
    @GetMapping("/users/{userId}/total-amount")
    public ResponseEntity<Long> getTotalAmount(
            @PathVariable Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {

        return ResponseEntity.ok(orderService.getTotalPaymentAmount(userId, since));
    }
}