package com.vatti.chzscout.backend.stream.domain;

import java.util.List;

/** 추천 결과로 반환되는 방송 정보. */
public record Stream(
    int liveId,
    String liveTitle,
    String liveThumbnailImageUrl,
    Integer concurrentUserCount,
    String channelId,
    String channelName,
    String liveCategoryValue,
    List<String> enrichedTags) {

  public static Stream from(EnrichedStreamDto dto) {
    return new Stream(
        dto.liveId(),
        dto.liveTitle(),
        dto.liveThumbnailImageUrl(),
        dto.concurrentUserCount(),
        dto.channelId(),
        dto.channelName(),
        dto.liveCategoryValue(),
        dto.enrichedTags());
  }
}
