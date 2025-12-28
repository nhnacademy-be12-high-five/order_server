package com.nhnacademy.order_server.handler;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.order_server.exception.GlobalExceptionHandler;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class,
        properties = "spring.cloud.config.enabled=false")

@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestConfig.class})
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public TestController testController() {
            return new TestController();
        }
    }

    /**
     * 테스트를 위한 임시 컨트롤러
     */
    @RestController
    static class TestController {
        @GetMapping("/test/4xx")
        public void throw4xxException() {
            throw new OrderException(OrderErrorCode.ORDER_NOT_FOUND);
        }

        @GetMapping("/test/5xx")
        public void throw5xxException() {
            throw new OrderException(OrderErrorCode.EXTERNAL_API_ERROR);
        }

        @PostMapping("/test/validation")
        public void validate(@Valid @RequestBody TestDto dto) {
            // 검증 통과 여부 확인용
        }
    }

    @Getter
    @NoArgsConstructor
    static class TestDto {
        @NotNull(message = "널이면 안됩니다")
        private String name;
    }

    @Test
    @DisplayName("OrderException(4xx): 클라이언트 에러는 그대로 응답 메시지 반환")
    void handleOrderException_4xx() throws Exception {
        mockMvc.perform(get("/test/4xx"))
                .andExpect(status().isNotFound()) // 404
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("주문을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("OrderException(5xx): 서버 에러는 로그를 남기고 메시지를 숨김")
    void handleOrderException_5xx() throws Exception {
        mockMvc.perform(get("/test/5xx"))
                .andExpect(status().isInternalServerError()) // 500
                .andExpect(jsonPath("$.error").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
    }

    @Test
    @DisplayName("MethodArgumentNotValidException: @Valid 검증 실패 시 400 반환")
    void handleValidationException() throws Exception {
        TestDto invalidDto = new TestDto(); // name is null

        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest()) // 400
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("name: 널이면 안됩니다"));
    }
}