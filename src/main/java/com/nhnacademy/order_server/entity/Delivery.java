package com.nhnacademy.order_server.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name ="delivery")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Delivery {

    @Id
    @Column(name = "id2")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id2")
    private Order order;

    @Column(name = "req_dlv_dt", nullable = false)
    private LocalDate requestDeliveryDate;

    @Column(name = "est_dlv_dt", nullable = false)
    private LocalDate estimatedDeliveryDate;

    @Column(name = "act_ship_dt")
    private LocalDateTime actualShipDate;

    @Column(name = "act_comp_dt")
    private LocalDateTime actualCompletionDate;
}
