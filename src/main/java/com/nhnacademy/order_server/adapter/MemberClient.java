package com.nhnacademy.order_server.adapter;

import com.nhnacademy.order_server.dto.request.PointTransactionCreateRequest;
import com.nhnacademy.order_server.dto.request.PointTransactionRequest;
import com.nhnacademy.order_server.dto.response.external.MemberGradeResponse;
import com.nhnacademy.order_server.dto.response.external.PointBalanceResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "TEAM5-MEMBER-SERVER")
public interface MemberClient {

    // [회원 등급 조회]
    @GetMapping("/api/members/{userId}/grade")
    MemberGradeResponse getMemberGrade(@PathVariable("userId") Long userId);

    // [포인트 잔액 조회] - URL 변경됨
    @GetMapping("/internal/point-transactions/{userId}")
    PointBalanceResponse getPointBalance(@PathVariable("userId") Long userId);

    // =================================================================
    // [통합 포인트 트랜잭션] (적립, 반품 시 환불/회수 등)
    // =================================================================
    @PostMapping("/internal/point-transactions")
    void createTransaction(@RequestBody PointTransactionCreateRequest request);


    // =================================================================
    // [TCC 패턴] 주문/결제 프로세스 (예약 -> 확정 or 취소)
    // =================================================================

    // 1. 포인트 사용 예약 (Reserve)
    @PostMapping("/internal/point-transactions/tcc/reserve")
    void reservePoint(@RequestBody PointTransactionRequest request);

    // 2. 포인트 사용 확정 (Confirm)
    @PostMapping("/internal/point-transactions/tcc/confirm")
    void confirmPoint(@RequestBody PointTransactionRequest request);

    // 3. 포인트 사용 취소 (Cancel - 결제 실패/취소 시 롤백)
    @PostMapping("/internal/point-transactions/tcc/cancel")
    void cancelPoint(@RequestBody PointTransactionRequest request);
}