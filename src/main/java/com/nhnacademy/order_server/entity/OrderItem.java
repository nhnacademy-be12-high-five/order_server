package com.nhnacademy.order_server.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "order_item")
@Getter
@Builder // [추가] 빌더 패턴
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    private Long id;

    @Column(name= "qty", nullable = false)
    private Integer quantity;

    @Column(name = "unit_prc", nullable = false)
    private Integer unitPrice;

    @Column(name = "is_wrp", nullable = false)
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

    // 연관관계 편의 메서드
    public void setOrder(Order order) {
        this.order = order;
    }
}
