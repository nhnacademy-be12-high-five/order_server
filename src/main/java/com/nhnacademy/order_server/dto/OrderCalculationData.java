package com.nhnacademy.order_server.dto;

import com.nhnacademy.order_server.entity.OrderItem;
import java.util.List;
import lombok.Builder;

@Builder
public record OrderCalculationData(
        List<OrderItem> tempOrderItems,
        int totalProductAmount,
        int totalWrappingFee,
        int totalEarnedPoint,
        String firstBookTitle
) {}