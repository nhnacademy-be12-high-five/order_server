package com.nhnacademy.order_server.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PointTransactionCreateRequest {
    private Long memberId;
    private String pointEventType; // EARN, USE, CANCEL_USE, CANCEL_EARN
    private Long amount;
    private Long orderId;
    private String description;
}