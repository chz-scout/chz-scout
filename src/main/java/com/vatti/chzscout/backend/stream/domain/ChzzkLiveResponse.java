package com.vatti.chzscout.backend.stream.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 치지직 Open API 생방송 목록 조회 응답. */
public record ChzzkLiveResponse(
    @JsonProperty("code") Integer code,
    @JsonProperty("message") String message,
    @JsonProperty("content") Content content) {

  /** 응답 본문. */
  public record Content(
      @JsonProperty("data") List<AllFieldLiveDto> data, @JsonProperty("page") Page page) {}

  /** 페이지네이션 정보. */
  public record Page(@JsonProperty("next") String next) {}

  /** data 접근 편의 메서드. */
  public List<AllFieldLiveDto> data() {
    return content != null ? content.data() : null;
  }

  /** page 접근 편의 메서드. */
  public Page page() {
    return content != null ? content.page() : null;
  }
}
