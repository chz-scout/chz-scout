package com.vatti.chzscout.backend.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 모든 도메인별 ErrorCode enum이 구현해야 하는 공통 인터페이스.
 *
 * <p>각 도메인에서 자체 ErrorCode enum을 정의하고 이 인터페이스를 구현하면, BusinessException에서 통일된 방식으로 처리할 수 있습니다.
 */
public interface ErrorCode {

  HttpStatus getHttpStatus();

  String getCode();

  String getMessage();
}
