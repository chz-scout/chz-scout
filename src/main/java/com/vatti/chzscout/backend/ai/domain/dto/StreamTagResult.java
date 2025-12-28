package com.vatti.chzscout.backend.ai.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 개별 방송의 태그 추출 결과.
 *
 * <p>배치 처리 시 각 방송별 결과를 담습니다.
 */
public class StreamTagResult {

  @JsonProperty("channelId")
  private String channelId;

  @JsonProperty("originalTags")
  private List<String> originalTags;

  @JsonProperty("aiTags")
  private List<String> aiTags;

  @JsonProperty("confidence")
  private double confidence;

  public StreamTagResult() {}

  public StreamTagResult(
      String channelId, List<String> originalTags, List<String> aiTags, double confidence) {
    this.channelId = channelId;
    this.originalTags = originalTags;
    this.aiTags = aiTags;
    this.confidence = confidence;
  }

  public String channelId() {
    return channelId;
  }

  /** 변경 감지용 - 입력으로 받은 원본 태그 (카테고리 + 기존 태그). */
  public List<String> originalTags() {
    return originalTags;
  }

  /** 추천 매칭용 - 전체 태그 (원본 + 제목 키워드 + AI 의미 태그). */
  public List<String> aiTags() {
    return aiTags;
  }

  public double confidence() {
    return confidence;
  }
}
