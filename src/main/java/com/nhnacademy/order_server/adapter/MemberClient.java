package com.nhnacademy.order_server.adapter;

import com.nhnacademy.order_server.dto.response.external.MemberGradeResponse; // DTO 정의 필요

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "member-service", url = "${member.service.url}")
public interface MemberClient {

    @GetMapping("/api/members/{userId}/grade")
    MemberGradeResponse getMemberGrade(@PathVariable("userId") Long userId);

    @GetMapping("/api/members/{userId}/point-balance")
    Integer getPointBalance(@PathVariable("userId") Long userId);

    @PostMapping("/api/members/{userId}/point-deduct")
    void deductPoint(@PathVariable("userId") Long userId, @RequestParam("amount") Integer amount);
}