package com.nhnacademy.order_server.dto.response;

import java.util.List;
import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
public class CommonPageResponse<T> {
    private final List<T> data;
    private final long totalElements;
    private final int totalPages;
    private final int pageNumber;
    private final int pageSize;
    private final boolean isLast;

    public CommonPageResponse(Page<T> page) {
        this.data = page.getContent();
        this.totalElements = page.getTotalElements();
        this.totalPages = page.getTotalPages();
        this.pageNumber = page.getNumber();
        this.pageSize = page.getSize();
        this.isLast = page.isLast();
    }

    public static <T> CommonPageResponse<T> from(Page<T> page) {
        return new CommonPageResponse<>(page);
    }
}