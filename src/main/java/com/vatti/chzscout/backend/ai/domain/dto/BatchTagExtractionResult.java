package com.vatti.chzscout.backend.ai.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 배치 태그 추출 결과 DTO.
 *
 * <p>여러 방송의 태그 추출 결과를 한 번에 담습니다.
 */
public class BatchTagExtractionResult {

  @JsonProperty("results")
  private List<StreamTagResult> results;

  public BatchTagExtractionResult() {}

  public BatchTagExtractionResult(List<StreamTagResult> results) {
    this.results = results;
  }

  public List<StreamTagResult> results() {
    return results;
  }

  /**
   * channelId를 키로 하는 Map으로 변환합니다.
   *
   * @return channelId → StreamTagResult 맵
   */
  public Map<String, StreamTagResult> toMap() {
    return results.stream()
        .collect(
            Collectors.toMap(
                StreamTagResult::channelId,
                result -> result,
                (existing, replacement) -> {
                  throw new IllegalArgumentException(
                      "Duplicate channelId found: " + existing.channelId());
                }));
  }
}
