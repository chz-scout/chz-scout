package com.vatti.chzscout.backend.ai.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * AI 태그 추출 결과 DTO.
 *
 * <p>OpenAI Structured Output으로 반환되는 태그 추출 결과입니다.
 *
 * <p>태그는 두 가지 유형으로 분리되어 반환됩니다:
 *
 * <ul>
 *   <li>originalTags: 입력으로 받은 태그들 (카테고리 + 기존 태그) - 변경 감지용
 *   <li>aiTags: AI가 생성한 전체 태그 목록 (기존 태그 포함 + 제목 키워드 + 의미 태그)
 * </ul>
 */
public class TagExtractionResult {

  @JsonProperty("originalTags")
  public List<String> originalTags;

  @JsonProperty("aiTags")
  public List<String> aiTags;

  @JsonProperty("confidence")
  public double confidence;

  public TagExtractionResult() {}

  public TagExtractionResult(List<String> originalTags, List<String> aiTags, double confidence) {
    this.originalTags = originalTags;
    this.aiTags = aiTags;
    this.confidence = confidence;
  }

  public List<String> originalTags() {
    return originalTags;
  }

  public List<String> aiTags() {
    return aiTags;
  }

  public double confidence() {
    return confidence;
  }
}
