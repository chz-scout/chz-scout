package com.vatti.chzscout.backend.stream.fixture;

import com.vatti.chzscout.backend.stream.domain.AllFieldLiveDto;
import java.util.List;

/** AllFieldLiveDto 테스트 픽스처. */
public class AllFieldLiveDtoFixture {

  private AllFieldLiveDtoFixture() {}

  /** 기본 라이브 데이터 생성. */
  public static AllFieldLiveDto create() {
    return create(1);
  }

  /** 인덱스 기반 라이브 데이터 생성. */
  public static AllFieldLiveDto create(int index) {
    return new AllFieldLiveDto(
        index,
        "테스트 방송 제목 " + index,
        "https://thumbnail.example.com/live" + index + ".jpg",
        1000 + index * 100,
        "2025-01-01T12:00:00",
        false,
        List.of("게임", "롤"),
        "GAME",
        "League of Legends",
        "리그 오브 레전드",
        "channel_" + index,
        "스트리머" + index,
        "https://profile.example.com/channel" + index + ".jpg");
  }

  /** 인덱스 기반 라이브 데이터 생성. */
  public static AllFieldLiveDto create(int index, List<String> tags) {
    return new AllFieldLiveDto(
        index,
        "테스트 방송 제목 " + index,
        "https://thumbnail.example.com/live" + index + ".jpg",
        1000 + index * 100,
        "2025-01-01T12:00:00",
        false,
        tags,
        "GAME",
        "League of Legends",
        "리그 오브 레전드",
        "channel_" + index,
        "스트리머" + index,
        "https://profile.example.com/channel" + index + ".jpg");
  }

  /** 성인 방송 데이터 생성. */
  public static AllFieldLiveDto adult(int index) {
    return new AllFieldLiveDto(
        index,
        "성인 방송 " + index,
        "https://thumbnail.example.com/adult" + index + ".jpg",
        500 + index * 50,
        "2025-01-01T22:00:00",
        true,
        List.of("토크", "성인"),
        "TALK",
        "Just Chatting",
        "토크/캠방",
        "adult_channel_" + index,
        "성인스트리머" + index,
        "https://profile.example.com/adult" + index + ".jpg");
  }

  /** 커스텀 카테고리 라이브 데이터 생성. */
  public static AllFieldLiveDto withCategory(int index, String categoryType, String category) {
    return new AllFieldLiveDto(
        index,
        category + " 방송 " + index,
        "https://thumbnail.example.com/live" + index + ".jpg",
        1000 + index * 100,
        "2025-01-01T12:00:00",
        false,
        List.of(category),
        categoryType,
        category,
        category,
        "channel_" + index,
        "스트리머" + index,
        "https://profile.example.com/channel" + index + ".jpg");
  }

  /** 높은 시청자 수 라이브 데이터 생성. */
  public static AllFieldLiveDto popular(int index, int viewerCount) {
    return new AllFieldLiveDto(
        index,
        "인기 방송 " + index,
        "https://thumbnail.example.com/popular" + index + ".jpg",
        viewerCount,
        "2025-01-01T12:00:00",
        false,
        List.of("인기", "추천"),
        "GAME",
        "League of Legends",
        "리그 오브 레전드",
        "popular_channel_" + index,
        "인기스트리머" + index,
        "https://profile.example.com/popular" + index + ".jpg");
  }
}
