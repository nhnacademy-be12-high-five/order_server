package com.nhnacademy.order_server.adapter;

import com.nhnacademy.order_server.dto.request.StockRequest;
import com.nhnacademy.order_server.dto.response.external.BookInfoResponse;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "TEAM5-BOOK-SERVER")
public interface BookClient {

    @PostMapping("/api/books/bulk")
    ResponseEntity<List<BookInfoResponse>> getBooksBulk(@RequestBody List<Long> bookIds);

    // 재고 선점 (Batch)
    @PostMapping("/api/books/stock/hold/batch")
    ResponseEntity<Void> holdStockBatch(@RequestBody List<StockRequest> requests,
                                        @RequestParam("orderKey") String orderKey);

    // 재고 확정 (Confirm)
    @PostMapping("/api/books/stock/confirm-deduction")
    ResponseEntity<Void> confirmStockDeduction(@RequestBody List<Long> bookIds,
                                               @RequestParam("orderKey") String orderKey);

    // 재고 복구 (Cancel/Refund)
    @PostMapping("/api/books/stock/restore")
    ResponseEntity<Void> restoreStock(@RequestBody List<StockRequest> requests,
                                      @RequestHeader("Idempotency-Key") String idempotencyKey);

    // 단순 선점 해제 (Try Cancel)
    @PostMapping("/api/books/release-stock")
    ResponseEntity<Void> releaseHeldStock(@RequestBody List<Long> bookIds,
                                          @RequestParam("orderKey") String orderKey);
}