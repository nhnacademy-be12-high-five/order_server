package com.nhnacademy.order_server.controller.swagger;

import com.nhnacademy.order_server.dto.request.WrapperRegisterRequest;
import com.nhnacademy.order_server.dto.response.WrapperResponse;
import com.nhnacademy.order_server.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Admin Wrapper API", description = "관리자 포장지 관리")
@RequestMapping("/api/admin/wrappers")
public interface AdminWrapperControllerDocs {

    @Operation(summary = "포장지 등록", description = "새로운 포장지를 등록합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 데이터",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    ResponseEntity<Void> createWrapper(@Valid @RequestBody WrapperRegisterRequest request);

    @Operation(summary = "포장지 수정", description = "기존 포장지 정보를 수정합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "404", description = "포장지를 찾을 수 없음 (WRAPPER_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 데이터",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{wrapperId}")
    ResponseEntity<Void> updateWrapper(@PathVariable Long wrapperId, @Valid @RequestBody WrapperRegisterRequest request);

    @Operation(summary = "포장지 삭제", description = "포장지를 삭제(사용 불가 처리)합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "삭제(비활성화) 성공"),
            @ApiResponse(responseCode = "404", description = "포장지를 찾을 수 없음 (WRAPPER_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{wrapperId}")
    ResponseEntity<Void> deleteWrapper(@PathVariable Long wrapperId);

    @Operation(summary = "포장지 전체 목록 조회", description = "모든 포장지 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    ResponseEntity<List<WrapperResponse>> getAllWrappers();
}