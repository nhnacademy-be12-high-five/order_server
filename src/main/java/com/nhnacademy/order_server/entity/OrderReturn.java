package com.nhnacademy.order_server.entity;

import com.nhnacademy.order_server.entity.enums.ReturnReason;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_return")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderReturn {

    @Id
    @Column(name = "order_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "ret_dt", nullable = false)
    private LocalDateTime returnDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "ret_rsn", nullable = false)
    private ReturnReason returnReason;

    @Column(name = "ret_ship_fee", nullable = false)
    private Integer returnShippingFee;

    @Column(name = "is_pnt_crd", nullable = false)
    private Boolean isPointCredited;
}
