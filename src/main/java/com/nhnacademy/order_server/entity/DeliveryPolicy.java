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
    private Long id;

    @Column(name = "min_order_amount", nullable = false)
    private Integer minOrderAmount;

    @Column(name = "standard_shipping_fee", nullable = false)
    private Integer standardShippingFee;

    @Column(name = "remote_area_surcharge")
    private Integer remoteAreaSurcharge;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "effective_date", nullable = false)
    private LocalDateTime effectiveDate;

    public DeliveryPolicy(Integer minOrderAmount, Integer standardShippingFee){
        this.minOrderAmount = minOrderAmount;
        this.standardShippingFee = standardShippingFee;
        this.isActive = true;
        this.effectiveDate = LocalDateTime.now();
    }

    public void deactivate(){
        this.isActive =false;
    }
}