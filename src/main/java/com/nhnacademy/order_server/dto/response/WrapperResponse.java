package com.nhnacademy.order_server.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WrapperResponse {

    private Long id;
    private String name;
    private Integer price;
}
