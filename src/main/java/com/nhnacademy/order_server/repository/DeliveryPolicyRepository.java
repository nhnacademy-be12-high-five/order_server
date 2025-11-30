package com.nhnacademy.order_server.repository;

import com.nhnacademy.order_server.entity.DeliveryPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeliveryPolicyRepository extends JpaRepository<DeliveryPolicy, Long> {
    Optional<DeliveryPolicy> findByIsActiveTrue();
}