package com.vatti.chzscout.backend.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vatti.chzscout.backend.common.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

  private boolean success;
  private T data;
  private ErrorResponse error;

  private ApiResponse(boolean success, T data, ErrorResponse error) {
    this.success = success;
    this.data = data;
    this.error = error;
  }

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, data, null);
  }

  public static <T> ApiResponse<T> success() {
    return new ApiResponse<>(true, null, null);
  }

  public static <T> ApiResponse<T> error(String code, String message) {
    return new ApiResponse<>(false, null, new ErrorResponse(code, message));
  }

  public static <T> ApiResponse<T> error(ErrorCode errorCode) {
    return new ApiResponse<>(
        false, null, new ErrorResponse(errorCode.getCode(), errorCode.getMessage()));
  }

  @Getter
  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static class ErrorResponse {
    private String code;
    private String message;

    private ErrorResponse(String code, String message) {
      this.code = code;
      this.message = message;
    }
  }
}
