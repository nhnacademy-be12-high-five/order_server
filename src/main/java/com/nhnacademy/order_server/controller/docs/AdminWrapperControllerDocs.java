package com.nhnacademy.order_server.controller.docs;

import com.nhnacademy.order_server.dto.request.WrapperRegisterRequest;
import com.nhnacademy.order_server.dto.response.WrapperResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin Wrapper API", description = "관리자 포장지 관리")
@RequestMapping("/api/admin/wrappers")
public interface AdminWrapperControllerDocs {

    @Operation(summary = "포장지 등록")
    @PostMapping
    ResponseEntity<Void> createWrapper(@Valid @RequestBody WrapperRegisterRequest request);

    @Operation(summary = "포장지 수정")
    @PutMapping("/{wrapperId}")
    ResponseEntity<Void> updateWrapper(@PathVariable Long wrapperId, @Valid @RequestBody WrapperRegisterRequest request);

    @Operation(summary = "포장지 삭제")
    @DeleteMapping("/{wrapperId}")
    ResponseEntity<Void> deleteWrapper(@PathVariable Long wrapperId);

    @Operation(summary = "포장지 전체 목록 조회")
    @GetMapping
    ResponseEntity<List<WrapperResponse>> getAllWrappers();
}