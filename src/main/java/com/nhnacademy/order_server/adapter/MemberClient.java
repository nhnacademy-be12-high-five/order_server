package com.nhnacademy.order_server.adapter;

import com.nhnacademy.order_server.dto.response.external.MemberGradeResponse;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "TEAM5-MEMBER-SERVER", contextId = "memberClient")
public interface MemberClient {

    @GetMapping("/api/members/{userId}/grade")
    MemberGradeResponse getMemberGrade(@PathVariable("userId") Long userId);

    @GetMapping("/api/members/{userId}/point-balance")
    Integer getPointBalance(@PathVariable("userId") Long userId);

    @PostMapping("/api/members/{userId}/point-deduct")
    void deductPoint(@PathVariable("userId") Long userId, @RequestParam("amount") Integer amount);

    @PostMapping("/api/members/{memberId}/point/reserve")
    ResponseEntity<Void> reservePoint(@PathVariable("memberId") Long memberId,
                                      @RequestParam("amount") int amount,
                                      @RequestParam("orderId") Long orderId);

    @PostMapping("/api/members/{userId}/point/cancel")
    void cancelPoint(@PathVariable("userId") Long userId,
                     @RequestParam("amount") Integer amount,
                     @RequestParam("orderId") Long orderId); // [추가] orderId 파라미터

    // [TCC 2단계] 포인트 사용 확정
    @PostMapping("/api/members/{userId}/point/confirm")
    void confirmPoint(@PathVariable("userId") Long userId,
                      @RequestParam("amount") Integer amount,
                      @RequestParam("orderId") Long orderId); // [추가] orderId 파라미터
}