package com.vatti.chzscout.backend.ai.domain.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 유저 메시지 분석 결과 DTO.
 *
 * <p>유저가 보낸 메시지의 의도, 추출된 의미 태그, 키워드, 그리고 선택적 응답을 담습니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserMessageAnalysisResult {

  private String intent;

  @JsonProperty("semantic_tags")
  @JsonAlias("semanticTags")
  private List<String> semanticTags;

  private List<String> keywords;

  private String reply;

  /** 방송 추천 요청인지 확인합니다. */
  public boolean isRecommendationRequest() {
    return "recommendation".equals(intent);
  }

  /** 특정 스트리머/방송 검색 요청인지 확인합니다. */
  public boolean isSearchRequest() {
    return "search".equals(intent);
  }

  /** 일반 대화(추천/검색 외)인지 확인합니다. */
  public boolean isOtherRequest() {
    return "other".equals(intent);
  }

  /** 바로 reply를 사용해야 하는 요청인지 확인합니다. (search, other) */
  public boolean hasDirectReply() {
    return reply != null && !reply.isBlank();
  }

  /** 키워드가 존재하는지 확인합니다. */
  public boolean hasKeywords() {
    return keywords != null && !keywords.isEmpty();
  }

  /** 의미 태그가 존재하는지 확인합니다. */
  public boolean hasSemanticTags() {
    return semanticTags != null && !semanticTags.isEmpty();
  }
}
