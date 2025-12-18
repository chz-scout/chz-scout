package com.vatti.chzscout.backend.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 공통 에러 코드.
 *
 * <p>도메인에 속하지 않는 범용 에러를 정의합니다.
 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_001", "서버 내부 오류가 발생했습니다."),
  INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON_002", "잘못된 입력값입니다."),
  ;

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
