package com.nhnacademy.order_server.entity;

import com.nhnacademy.order_server.entity.enums.DeliveryStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "orders")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    @Column(name = "order_key", unique = true, nullable = false)
    private String orderKey;

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

    @Column(name = "cpn_disc_amt")
    private Integer couponDiscount;

    @Column(name = "pnt_use_amt")
    private Integer pointDiscount;

    @Column(name = "pnt_earn_amt")
    private Integer earnedPoint;

    @Setter
    @Column(name = "payment_key")
    private String paymentKey;

    @Column(name = "coupon_id")
    @Setter
    private Long couponId;

    @Column(name = "ord_pw")
    private String orderPassword;

    @Column(name = "user_id")
    private Long userId;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> orderItems = new ArrayList<>();

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL)
    private Delivery delivery;

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL)
    private OrderReturn orderReturn;

    @Column(name = "pending_ops")
    private String pendingOperations;

    public void addOrderItem(OrderItem orderItem) {
        this.orderItems.add(orderItem);
        orderItem.setOrder(this);
    }

    public void updateStatus(DeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public void setPendingOperations(List<String> failedOps) {
        if (failedOps == null || failedOps.isEmpty()) {
            this.pendingOperations = null;
        } else {
            this.pendingOperations = String.join(",", failedOps);
        }
    }

}