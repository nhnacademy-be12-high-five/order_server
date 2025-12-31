package com.nhnacademy.order_server.service.impl;

import com.nhnacademy.order_server.dto.request.WrapperRegisterRequest;
import com.nhnacademy.order_server.dto.response.WrapperResponse;
import com.nhnacademy.order_server.entity.Wrapper;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import com.nhnacademy.order_server.repository.WrapperRepository;
import com.nhnacademy.order_server.service.WrapperService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WrapperServiceImpl implements WrapperService {

    private final WrapperRepository wrapperRepository;

    @Override
    @Transactional
    public void createWrapper(WrapperRegisterRequest request) {
        wrapperRepository.save(request.toEntity());
    }

    @Override
    @Transactional
    public void updateWrapper(Long wrapperId, WrapperRegisterRequest request) {
        Wrapper wrapper = wrapperRepository.findById(wrapperId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.WRAPPER_NOT_FOUND));

        wrapper.update(request.getWrapperName(), request.getWrapperPrice());
    }

    @Override
    @Transactional
    public void deleteWrapper(Long wrapperId) {
        Wrapper wrapper = wrapperRepository.findById(wrapperId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.WRAPPER_NOT_FOUND));

        wrapper.changeAvailability(false);
    }

    @Override
    public List<WrapperResponse> getAllWrappers() {
        return wrapperRepository.findAll().stream()
                .map(WrapperResponse::from)
                .toList();
    }

    @Override
    public List<WrapperResponse> getAvailableWrappers() {
        return wrapperRepository.findByIsAvailableTrue().stream()
                .map(WrapperResponse::from)
                .toList();
    }
}