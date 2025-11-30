package com.nhnacademy.order_server.controller;

import com.nhnacademy.order_server.controller.docs.AdminWrapperControllerDocs;
import com.nhnacademy.order_server.dto.request.WrapperRegisterRequest;
import com.nhnacademy.order_server.dto.response.WrapperResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AdminWrapperController implements AdminWrapperControllerDocs {

    // private final WrapperService wrapperService; // 나중에 주입

    @Override
    public ResponseEntity<Void> createWrapper(WrapperRegisterRequest request) {
        return ResponseEntity.status(201).build();
    }

    @Override
    public ResponseEntity<Void> updateWrapper(Long wrapperId, WrapperRegisterRequest request) {
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> deleteWrapper(Long wrapperId) {
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<WrapperResponse>> getAllWrappers() {
        return ResponseEntity.ok(List.of(
                WrapperResponse.builder().id(1L).name("테스트 포장지").price(1000).build()
        ));
    }
}