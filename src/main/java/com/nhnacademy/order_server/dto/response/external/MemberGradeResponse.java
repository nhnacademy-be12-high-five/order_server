package com.nhnacademy.order_server.dto.response.external;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MemberGradeResponse {
    private String gradeName;
    private Double earnRate;
}