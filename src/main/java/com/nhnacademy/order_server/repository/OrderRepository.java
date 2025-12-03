package com.nhnacademy.order_server.repository;

import com.nhnacademy.order_server.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order,Long> {


}
