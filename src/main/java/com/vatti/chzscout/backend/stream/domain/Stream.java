package com.vatti.chzscout.backend.stream.domain;

import com.vatti.chzscout.backend.tag.domain.entity.Tag;
import java.util.List;

public record Stream(
    Integer liveId,
    String liveTitle,
    String liveThumbnailImageUrl,
    Integer concurrentUserCount,
    String openDate,
    Boolean adult,
    List<Tag> tags,
    Tag liveCategory,
    String channelId,
    String channelName,
    String channelImageUrl) {

  public static Stream from(AllFieldLiveDto allFieldLiveDto) {
    List<Tag> tags = allFieldLiveDto.tags().stream().map(Tag::createCustom).toList();
    Tag liveCategory = Tag.createCategory(allFieldLiveDto.liveCategory());
    return new Stream(
        allFieldLiveDto.liveId(),
        allFieldLiveDto.liveTitle(),
        allFieldLiveDto.liveThumbnailImageUrl(),
        allFieldLiveDto.concurrentUserCount(),
        allFieldLiveDto.openDate(),
        allFieldLiveDto.adult(),
        tags,
        liveCategory,
        allFieldLiveDto.channelId(),
        allFieldLiveDto.channelName(),
        allFieldLiveDto.channelImageUrl());
  }
}
