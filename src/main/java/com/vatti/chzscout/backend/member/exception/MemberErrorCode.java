package com.vatti.chzscout.backend.member.exception;

import com.vatti.chzscout.backend.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {
  MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_001", "회원을 찾을 수 없습니다."),
  ;

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
