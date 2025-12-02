package com.nhnacademy.order_server.dto.response;

import com.nhnacademy.order_server.entity.Wrapper;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WrapperResponse {

    private Long id;
    private String name;
    private Integer price;

    public static WrapperResponse from(Wrapper wrapper) {
        return WrapperResponse.builder()
                .id(wrapper.getId())
                .name(wrapper.getWrapperName())
                .price(wrapper.getWrapperPrice())
                .build();
    }
}
