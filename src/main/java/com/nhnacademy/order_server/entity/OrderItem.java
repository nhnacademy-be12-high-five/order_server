package com.nhnacademy.order_server.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name= "qty", nullable = false)
    private Integer quantity;

    @Column(name = "unit_prc", nullable = false)
    private boolean isWrapped;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name="key_val")
    private String key;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wrp_id")
    private Wrapper wrapper;
}
