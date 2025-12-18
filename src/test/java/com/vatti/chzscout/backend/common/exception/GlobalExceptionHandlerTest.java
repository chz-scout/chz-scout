package com.vatti.chzscout.backend.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.vatti.chzscout.backend.auth.exception.AuthErrorCode;
import com.vatti.chzscout.backend.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

  GlobalExceptionHandler handler;

  @BeforeEach
  void setUp() {
    handler = new GlobalExceptionHandler();
  }

  @Nested
  @DisplayName("handleBusinessException")
  class HandleBusinessException {

    @Test
    @DisplayName("BusinessException 발생 시 해당 ErrorCode의 상태코드와 메시지를 반환한다")
    void returnsCorrectStatusAndMessage() {
      // given
      BusinessException exception = new BusinessException(AuthErrorCode.INVALID_TOKEN);

      // when
      ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(exception);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().isSuccess()).isFalse();
      assertThat(response.getBody().getError().getCode()).isEqualTo("AUTH_001");
      assertThat(response.getBody().getError().getMessage()).isEqualTo("유효하지 않은 토큰입니다.");
    }

    @Test
    @DisplayName("CommonErrorCode로 BusinessException 발생 시 올바르게 처리한다")
    void handlesCommonErrorCode() {
      // given
      BusinessException exception = new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);

      // when
      ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(exception);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().getError().getCode()).isEqualTo("COMMON_002");
    }
  }

  @Nested
  @DisplayName("handleException")
  class HandleException {

    @Test
    @DisplayName("일반 Exception 발생 시 INTERNAL_SERVER_ERROR를 반환한다")
    void returnsInternalServerError() {
      // given
      Exception exception = new RuntimeException("알 수 없는 오류");

      // when
      ResponseEntity<ApiResponse<Void>> response = handler.handleException(exception);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().isSuccess()).isFalse();
      assertThat(response.getBody().getError().getCode()).isEqualTo("COMMON_001");
      assertThat(response.getBody().getError().getMessage()).isEqualTo("서버 내부 오류가 발생했습니다.");
    }

    @Test
    @DisplayName("NullPointerException도 INTERNAL_SERVER_ERROR로 처리한다")
    void handlesNullPointerException() {
      // given
      Exception exception = new NullPointerException();

      // when
      ResponseEntity<ApiResponse<Void>> response = handler.handleException(exception);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(response.getBody().getError().getCode()).isEqualTo("COMMON_001");
    }
  }
}
