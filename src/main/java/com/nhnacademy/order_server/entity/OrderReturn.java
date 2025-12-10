package com.nhnacademy.order_server.entity;

import com.nhnacademy.order_server.entity.enums.ReturnReason;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_return")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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

    @Column(name = "refund_amt", nullable = false)
    private Integer refundAmount;

    @Column(name = "desc_txt")
    private String description;

    @Column(name = "ret_ship_fee")
    private Integer returnShippingFee;

    @Column(name = "is_pnt_crd")
    private Boolean isPointCredited;

    @PrePersist
    protected void onCreate() {
        if (this.returnDate == null) {
            this.returnDate = LocalDateTime.now();
        }
    }
}
