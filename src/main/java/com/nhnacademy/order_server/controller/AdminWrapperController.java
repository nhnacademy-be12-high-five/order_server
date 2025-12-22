package com.nhnacademy.order_server.controller;

import com.nhnacademy.order_server.controller.swagger.AdminWrapperControllerDocs;
import com.nhnacademy.order_server.dto.request.WrapperRegisterRequest;
import com.nhnacademy.order_server.dto.response.WrapperResponse;
import com.nhnacademy.order_server.service.WrapperService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminWrapperController implements AdminWrapperControllerDocs {

    private final WrapperService wrapperService;

    @Override
    public ResponseEntity<Void> createWrapper(WrapperRegisterRequest request) {
        wrapperService.createWrapper(request);
        return ResponseEntity.status(201).build();
    }

    @Override
    public ResponseEntity<Void> updateWrapper(Long wrapperId, WrapperRegisterRequest request) {
        wrapperService.updateWrapper(wrapperId, request);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> deleteWrapper(Long wrapperId) {
        wrapperService.deleteWrapper(wrapperId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<WrapperResponse>> getAllWrappers() {
        return ResponseEntity.ok(wrapperService.getAllWrappers());
    }
}