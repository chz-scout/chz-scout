package com.vatti.chzscout.backend.stream.domain;

import java.util.List;

/** 치지직 Open API 생방송 목록 조회 응답의 개별 라이브 정보. */
public record AllFieldLiveDto(
    Integer liveId,
    String liveTitle,
    String liveThumbnailImageUrl,
    Integer concurrentUserCount,
    String openDate,
    Boolean adult,
    List<String> tags,
    String categoryType,
    String liveCategory,
    String liveCategoryValue,
    String channelId,
    String channelName,
    String channelImageUrl) {}
