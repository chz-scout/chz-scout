package com.vatti.chzscout.backend.stream.fixture;

import com.vatti.chzscout.backend.stream.domain.EnrichedStreamDto;
import java.util.List;

/** EnrichedStreamDto 테스트 픽스처. */
public class EnrichedStreamDtoFixture {

  private EnrichedStreamDtoFixture() {}

  /** 기본 Enriched 데이터 생성. */
  public static EnrichedStreamDto create() {
    return create(1);
  }

  /** 인덱스 기반 Enriched 데이터 생성. */
  public static EnrichedStreamDto create(int index) {
    List<String> originalTags = List.of("리그 오브 레전드", "게임", "롤");
    List<String> enrichedTags = List.of("리그 오브 레전드", "게임", "롤", "e스포츠", "MOBA", "경쟁");

    return new EnrichedStreamDto(
        index,
        "channel_" + index,
        "테스트 방송 제목 " + index,
        "https://thumbnail.example.com/live" + index + ".jpg",
        1000 + index * 100,
        "스트리머" + index,
        "리그 오브 레전드",
        originalTags,
        enrichedTags);
  }

  /** 커스텀 태그로 Enriched 데이터 생성. */
  public static EnrichedStreamDto withTags(
      int index, List<String> originalTags, List<String> enrichedTags) {
    return new EnrichedStreamDto(
        index,
        "channel_" + index,
        "테스트 방송 제목 " + index,
        "https://thumbnail.example.com/live" + index + ".jpg",
        1000 + index * 100,
        "스트리머" + index,
        "리그 오브 레전드",
        originalTags,
        enrichedTags);
  }

  /** 제목에 특정 키워드가 포함된 방송 생성 (제목 매칭 테스트용). */
  public static EnrichedStreamDto withTitle(int index, String title) {
    return new EnrichedStreamDto(
        index,
        "channel_" + index,
        title,
        "https://thumbnail.example.com/live" + index + ".jpg",
        1000 + index * 100,
        "스트리머" + index,
        "기타",
        List.of(),
        List.of());
  }

  /** 롤 방송 - 원본 태그에 "롤" 포함. */
  public static EnrichedStreamDto lolStream(int index) {
    return new EnrichedStreamDto(
        index,
        "channel_" + index,
        "즐겜 방송",
        "https://thumbnail.example.com/live" + index + ".jpg",
        1000 + index * 100,
        "스트리머" + index,
        "리그 오브 레전드",
        List.of("롤", "게임"),
        List.of("롤", "게임", "MOBA", "e스포츠"));
  }

  /** FPS 방송 - AI 태그에만 "게임" 포함. */
  public static EnrichedStreamDto fpsStream(int index) {
    return new EnrichedStreamDto(
        index,
        "channel_" + index,
        "총게 한판",
        "https://thumbnail.example.com/live" + index + ".jpg",
        500 + index * 50,
        "스트리머" + index,
        "배틀그라운드",
        List.of("배그", "FPS"),
        List.of("배그", "FPS", "게임", "슈팅"));
  }

  /** 매칭되지 않는 방송 (음악 방송). */
  public static EnrichedStreamDto musicStream(int index) {
    return new EnrichedStreamDto(
        index,
        "channel_" + index,
        "노래 부르기",
        "https://thumbnail.example.com/live" + index + ".jpg",
        2000,
        "가수" + index,
        "음악",
        List.of("음악", "노래"),
        List.of("음악", "노래", "보컬", "라이브"));
  }
}
