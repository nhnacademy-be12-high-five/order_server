package com.nhnacademy.order_server.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "deliver_policy")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "deliver_policy_id")
    private Integer id;

    @Column(name = "min_order_amount", nullable = false)
    private Integer minOrderAmount;

    @Column(name = "standard_shipping_fee", nullable = false)
    private Integer standardShippingFee;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "effective_date", nullable = false)
    private LocalDateTime effectiveDate;
}