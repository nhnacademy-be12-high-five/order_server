package com.nhnacademy.order_server.dto.response.external;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BookInfoResponse {

    private Long bookId;
    private Integer price;
    private Double accumulateRate;
    private String title;
}
