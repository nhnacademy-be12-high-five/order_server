package com.nhnacademy.order_server.service;

import com.nhnacademy.order_server.dto.request.WrapperRegisterRequest;
import com.nhnacademy.order_server.dto.response.WrapperResponse;
import java.util.List;

public interface WrapperService {


    void createWrapper(WrapperRegisterRequest request);
    void updateWrapper(Long wrapperId, WrapperRegisterRequest request);
    void deleteWrapper(Long wrapperId);
    List<WrapperResponse> getAllWrappers();
    List<WrapperResponse> getAvailableWrappers();
}
