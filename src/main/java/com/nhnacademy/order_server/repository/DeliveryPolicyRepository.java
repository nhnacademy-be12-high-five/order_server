package com.nhnacademy.order_server.repository;

import com.nhnacademy.order_server.entity.DeliveryPolicy;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryPolicyRepository extends JpaRepository<DeliveryPolicy, Long> {

    Optional<DeliveryPolicy> findByIsActiveTrue();
}