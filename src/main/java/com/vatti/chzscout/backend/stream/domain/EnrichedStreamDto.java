package com.vatti.chzscout.backend.stream.domain;

import com.vatti.chzscout.backend.ai.domain.dto.StreamTagResult;
import java.util.List;

/**
 * AI 태그가 추가된 방송 정보 DTO.
 *
 * <p>Redis 저장 및 추천 매칭에 사용됩니다.
 *
 * @param channelId 채널 ID (식별용)
 * @param liveTitle 방송 제목
 * @param liveThumbnailImageUrl 썸네일 URL
 * @param concurrentUserCount 동시 시청자 수
 * @param channelName 채널명
 * @param liveCategoryValue 카테고리 (예: "마인크래프트")
 * @param originalTags 변경 감지용 - 원본 태그 (카테고리 + 기존 태그)
 * @param enrichedTags 추천 매칭용 - 전체 태그 (원본 + 키워드 + AI 의미 태그)
 */
public record EnrichedStreamDto(
    int liveId,
    String channelId,
    String liveTitle,
    String liveThumbnailImageUrl,
    Integer concurrentUserCount,
    String channelName,
    String liveCategoryValue,
    List<String> originalTags,
    List<String> enrichedTags) {

  /**
   * AllFieldLiveDto와 AI 태그 추출 결과로부터 생성합니다.
   *
   * @param dto 치지직 API 원본 데이터
   * @param tagResult AI 태그 추출 결과
   * @return EnrichedStreamDto
   */
  public static EnrichedStreamDto from(AllFieldLiveDto dto, StreamTagResult tagResult) {
    return new EnrichedStreamDto(
        dto.liveId(),
        dto.channelId(),
        dto.liveTitle(),
        dto.liveThumbnailImageUrl(),
        dto.concurrentUserCount(),
        dto.channelName(),
        dto.liveCategoryValue(),
        tagResult.originalTags(),
        tagResult.aiTags());
  }

  /**
   * AI 태그 추출 없이 원본 데이터만으로 생성합니다.
   *
   * <p>태그 추출 실패 시 fallback으로 사용됩니다.
   *
   * @param dto 치지직 API 원본 데이터
   * @return EnrichedStreamDto (enrichedTags = 원본 tags)
   */
  public static EnrichedStreamDto fromWithoutAi(AllFieldLiveDto dto) {
    List<String> originalTags = buildOriginalTags(dto);
    return new EnrichedStreamDto(
        dto.liveId(),
        dto.channelId(),
        dto.liveTitle(),
        dto.liveThumbnailImageUrl(),
        dto.concurrentUserCount(),
        dto.channelName(),
        dto.liveCategoryValue(),
        originalTags,
        originalTags); // AI 없이는 원본 = enriched
  }

  private static List<String> buildOriginalTags(AllFieldLiveDto dto) {
    var tags = new java.util.ArrayList<String>();
    if (dto.liveCategoryValue() != null && !dto.liveCategoryValue().isBlank()) {
      tags.add(dto.liveCategoryValue());
    }
    if (dto.tags() != null) {
      tags.addAll(dto.tags());
    }
    return tags;
  }
}
