package com.vatti.chzscout.backend.example.exception;

import com.vatti.chzscout.backend.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ExampleErrorCode implements ErrorCode {
  EXAMPLE_NOT_FOUND(HttpStatus.NOT_FOUND, "EXAMPLE_001", "예제를 찾을 수 없습니다."),
  EXAMPLE_ALREADY_EXISTS(HttpStatus.CONFLICT, "EXAMPLE_002", "이미 존재하는 예제입니다."),
  ;

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
