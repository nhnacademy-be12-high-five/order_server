package com.nhnacademy.order_server.service;

public interface DeliveryService {

    int calculateDeliveryFee(Integer productAmount, String address);
}
