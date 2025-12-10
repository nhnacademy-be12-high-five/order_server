package com.nhnacademy.order_server.repository;

import com.nhnacademy.order_server.entity.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
}
