package com.nhnacademy.order_server.service;

import com.nhnacademy.order_server.dto.request.OrderReturnRequest;
import com.nhnacademy.order_server.dto.response.OrderReturnCheckResponse;

public interface OrderReturnService {
    OrderReturnCheckResponse checkReturnEligibility(Long orderId);
    void requestReturn(Long orderId, OrderReturnRequest request);
}