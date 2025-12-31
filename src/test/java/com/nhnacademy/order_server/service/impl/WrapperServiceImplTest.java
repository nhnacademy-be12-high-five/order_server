package com.nhnacademy.order_server.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.nhnacademy.order_server.dto.request.WrapperRegisterRequest;
import com.nhnacademy.order_server.dto.response.WrapperResponse;
import com.nhnacademy.order_server.entity.Wrapper;
import com.nhnacademy.order_server.exception.OrderErrorCode;
import com.nhnacademy.order_server.exception.OrderException;
import com.nhnacademy.order_server.repository.WrapperRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WrapperServiceImplTest {

    @InjectMocks
    private WrapperServiceImpl wrapperService;

    @Mock
    private WrapperRepository wrapperRepository;

    @Test
    @DisplayName("포장지 등록 성공")
    void createWrapper() {
        // given
        WrapperRegisterRequest request = new WrapperRegisterRequest();
        ReflectionTestUtils.setField(request, "wrapperName", "선물 포장");
        ReflectionTestUtils.setField(request, "wrapperPrice", 1000);

        // when
        wrapperService.createWrapper(request);

        // then
        verify(wrapperRepository).save(any(Wrapper.class));
    }

    @Test
    @DisplayName("포장지 수정 성공")
    void updateWrapper() {
        // given
        Long wrapperId = 1L;
        Wrapper wrapper = new Wrapper("옛날포장", 1000, true);
        given(wrapperRepository.findById(wrapperId)).willReturn(Optional.of(wrapper));

        WrapperRegisterRequest request = new WrapperRegisterRequest();
        ReflectionTestUtils.setField(request, "wrapperName", "새포장");
        ReflectionTestUtils.setField(request, "wrapperPrice", 2000);

        // when
        wrapperService.updateWrapper(wrapperId, request);

        // then
        assertThat(wrapper.getWrapperName()).isEqualTo("새포장");
        assertThat(wrapper.getWrapperPrice()).isEqualTo(2000);
    }

    @Test
    @DisplayName("없는 포장지 수정 시 예외 발생")
    void updateWrapper_Fail() {
        // given
        Long wrapperId = 999L;
        WrapperRegisterRequest request = new WrapperRegisterRequest();
        given(wrapperRepository.findById(wrapperId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> wrapperService.updateWrapper(wrapperId, request))
                .isInstanceOf(OrderException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.WRAPPER_NOT_FOUND);
    }

    @Test
    @DisplayName("포장지 삭제(Soft Delete) 성공")
    void deleteWrapper() {
        // given
        Long wrapperId = 1L;
        Wrapper wrapper = new Wrapper("포장지", 1000, true);
        given(wrapperRepository.findById(wrapperId)).willReturn(Optional.of(wrapper));

        // when
        wrapperService.deleteWrapper(wrapperId);

        // then
        assertThat(wrapper.isAvailable()).isFalse(); // Soft Delete 확인
    }

    @Test
    @DisplayName("전체 포장지 목록 조회")
    void getAllWrappers() {
        // given
        Wrapper w1 = new Wrapper("포장1", 100, true);
        ReflectionTestUtils.setField(w1, "id", 1L);
        Wrapper w2 = new Wrapper("포장2", 200, false);
        ReflectionTestUtils.setField(w2, "id", 2L);

        given(wrapperRepository.findAll()).willReturn(List.of(w1, w2));

        // when
        List<WrapperResponse> result = wrapperService.getAllWrappers();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("포장1");
        assertThat(result.get(1).getName()).isEqualTo("포장2");
    }
}