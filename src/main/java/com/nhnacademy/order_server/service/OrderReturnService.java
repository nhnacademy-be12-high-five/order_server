package com.nhnacademy.order_server.service;

import com.nhnacademy.order_server.dto.request.OrderReturnRequest;
import com.nhnacademy.order_server.dto.response.OrderReturnCheckResponse;
import com.nhnacademy.order_server.entity.enums.ReturnReason;

public interface OrderReturnService {
    OrderReturnCheckResponse checkReturnEligibility(Long orderId, ReturnReason returnReason);
    void requestReturn(Long orderId, OrderReturnRequest request);
}