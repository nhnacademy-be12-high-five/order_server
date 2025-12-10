package com.nhnacademy.order_server.repository;

import com.nhnacademy.order_server.entity.OrderReturn;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderReturnRepository extends JpaRepository<OrderReturn, Long> {
}
