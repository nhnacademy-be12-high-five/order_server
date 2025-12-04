package com.nhnacademy.order_server.service;

public interface DeliveryService {

    int calculateDeliveryFee(int productAmount, String address);
}
