package com.nhnacademy.order_server.entity;

import com.nhnacademy.order_server.entity.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "is_mbr", nullable = false)
    private Boolean isMember;

    @Column(name = "rcv_nm", nullable = false, length = 50)
    private String receiverName;

    @Column(name = "rcv_addr", nullable = false, length = 50)
    private String receiverAddress;

    @Column(name = "ord_dt", nullable = false)
    private LocalDateTime orderDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "dlv_stat", nullable = false)
    private DeliveryStatus deliveryStatus;

    @Column(name = "pay_amt", nullable = false)
    private Integer paymentAmount;

    @Column(name = "prod_amt", nullable = false)
    private Integer productAmount;

    @Column(name = "wrp_fee", nullable = false)
    private Integer wrappingFee;

    @Column(name = "dlv_fee", nullable = false)
    private Integer deliveryFee;

    @Column(name = "ord_pw")
    private Integer orderPassword;

    @Column(name = "user_id")
    private Long userId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> orderItems = new ArrayList<>();

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL)
    private Delivery delivery;

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL)
    private OrderReturn orderReturn;
}