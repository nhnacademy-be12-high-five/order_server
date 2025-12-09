package com.nhnacademy.order_server.adapter;

import com.nhnacademy.order_server.dto.response.external.BookInfoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "book-service", url = "${book.service.url}")
public interface BookClient {

    @GetMapping("/api/books/{bookId}/info")
    BookInfoResponse getBookInfo(@PathVariable("bookId") Long bookId);

    @PostMapping("/api/books/{bookId}/stock/hold")
    void holdStock(@PathVariable("bookId") Long bookId, @RequestParam("quantity") Integer quantity);

    @PostMapping("/api/books/stock/confirm-deduction")
    void confirmStockDeduction(@RequestBody List<Long> bookIds);

    @PostMapping("/api/books/release-stock")
    void releaseHeldStock(@RequestBody List<Long> bookIds);
}
