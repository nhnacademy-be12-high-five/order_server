package com.nhnacademy.order_server.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "wrapper")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wrapper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wrp_nm", nullable = false, length = 100)
    private String wrapperName;

    @Column(name ="wrp_price", nullable = false)
    private Integer wrapperPrice;

    @Column(name = "is_ava", nullable = false)
    private boolean isAvailable;
}
