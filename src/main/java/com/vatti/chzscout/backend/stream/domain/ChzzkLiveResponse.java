package com.vatti.chzscout.backend.stream.domain;

import java.util.List;

/** 치지직 Open API 생방송 목록 조회 응답. */
public record ChzzkLiveResponse(Integer code, String message, Content content) {

  /** 응답 본문. */
  public record Content(List<AllFieldLiveDto> data, Page page) {}

  /** 페이지네이션 정보. */
  public record Page(String next) {}

  /** data 접근 편의 메서드. */
  public List<AllFieldLiveDto> data() {
    return content != null ? content.data() : null;
  }

  /** page 접근 편의 메서드. */
  public Page page() {
    return content != null ? content.page() : null;
  }
}
