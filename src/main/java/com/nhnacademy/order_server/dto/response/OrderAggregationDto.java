package com.nhnacademy.order_server.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderAggregationDto {
    private Long userId;
    private Long totalAmount;
}