package com.vatti.chzscout.backend.stream.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 치지직 Open API 생방송 목록 조회 응답의 개별 라이브 정보. */
public record AllFieldLiveDto(
    @JsonProperty("liveId") Integer liveId,
    @JsonProperty("liveTitle") String liveTitle,
    @JsonProperty("liveThumbnailImageUrl") String liveThumbnailImageUrl,
    @JsonProperty("concurrentUserCount") Integer concurrentUserCount,
    @JsonProperty("openDate") String openDate,
    @JsonProperty("adult") Boolean adult,
    @JsonProperty("tags") List<String> tags,
    @JsonProperty("categoryType") String categoryType,
    @JsonProperty("liveCategory") String liveCategory,
    @JsonProperty("liveCategoryValue") String liveCategoryValue,
    @JsonProperty("channelId") String channelId,
    @JsonProperty("channelName") String channelName,
    @JsonProperty("channelImageUrl") String channelImageUrl) {}
