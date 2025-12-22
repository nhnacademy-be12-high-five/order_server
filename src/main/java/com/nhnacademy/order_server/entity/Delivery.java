package com.nhnacademy.order_server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name ="delivery")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Delivery {

    @Id
    @Column(name = "order_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "req_dlv_dt", nullable = false)
    private LocalDate requestDeliveryDate;

    @Column(name = "est_dlv_dt", nullable = false)
    private LocalDate estimatedDeliveryDate;

    @Column(name = "act_ship_dt")
    private LocalDateTime actualShipDate;

    @Column(name = "act_comp_dt")
    private LocalDateTime actualCompletionDate;

    @Column(name = "tracking_no")
    private String trackingNumber;

    public void startDelivery(String trackingNumber) {
        this.trackingNumber = trackingNumber;
        this.actualShipDate = LocalDateTime.now();
    }

    public void completeDelivery() {
        this.actualCompletionDate = LocalDateTime.now();
    }
}
