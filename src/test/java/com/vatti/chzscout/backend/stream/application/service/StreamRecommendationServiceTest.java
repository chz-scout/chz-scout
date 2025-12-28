package com.vatti.chzscout.backend.stream.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.vatti.chzscout.backend.stream.domain.EnrichedStreamDto;
import com.vatti.chzscout.backend.stream.domain.Stream;
import com.vatti.chzscout.backend.stream.fixture.EnrichedStreamDtoFixture;
import com.vatti.chzscout.backend.stream.infrastructure.redis.StreamRedisStore;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StreamRecommendationServiceTest {
  @InjectMocks StreamRecommendationService streamRecommendationService;
  @Mock StreamRedisStore streamRedisStore;

  // 테스트용 검색 태그
  static final List<String> SINGLE_TAG = List.of("롤");
  static final List<String> MULTIPLE_TAGS = List.of("롤", "게임", "FPS");
  static final List<String> EMPTY_TAGS = List.of();
  static final List<String> MIXED_CASE_TAGS = List.of("LOL", "Game");

  @Nested
  @DisplayName("recommend 메서드 테스트")
  class recommendTests {
    @Test
    @DisplayName("검색 태그와 매칭 점수가 높은 순으로 최대 5개의 방송을 리턴한다")
    void recommendTestSuccess() {
      // given
      List<EnrichedStreamDto> liveStreams =
          List.of(
              EnrichedStreamDtoFixture.withTitle(1, "롤 다이아 승급전"), // 제목 매칭 → 10점
              EnrichedStreamDtoFixture.lolStream(2), // 원본 태그 "롤" → 5점
              EnrichedStreamDtoFixture.withTags(
                  3, // AI 태그에만 "롤" → 2점
                  List.of("게임"),
                  List.of("게임", "롤", "MOBA")),
              EnrichedStreamDtoFixture.musicStream(4), // 매칭 없음 → 0점
              EnrichedStreamDtoFixture.fpsStream(5) // 매칭 없음 → 0점
              );
      given(streamRedisStore.findEnrichedStreams()).willReturn(liveStreams);

      // when
      List<Stream> recommend1 = streamRecommendationService.recommend(SINGLE_TAG);
      List<Stream> recommend2 = streamRecommendationService.recommend(MULTIPLE_TAGS);

      // then
      // SINGLE_TAG("롤"): 점수순 [1→10점, 2→5점, 3→2점], 4·5는 0점이라 제외
      assertThat(recommend1).hasSize(3);
      assertThat(recommend1.stream().map(Stream::liveId).toList()).containsExactly(1, 2, 3);

      // MULTIPLE_TAGS("롤","게임","FPS"): 점수순 [1→10점, 2→10점, 3→7점, 5→7점]
      assertThat(recommend2).hasSize(4);
      assertThat(recommend2.stream().map(Stream::liveId).toList()).containsExactly(1, 2, 3, 5);
    }

    @Test
    @DisplayName("매칭되는 방송이 없으면 빈 리스트를 반환한다")
    void returnsEmptyListWhenNoMatch() {
      // given
      List<EnrichedStreamDto> liveStreams =
          List.of(
              EnrichedStreamDtoFixture.musicStream(1), // "음악", "노래" 태그
              EnrichedStreamDtoFixture.musicStream(2));
      given(streamRedisStore.findEnrichedStreams()).willReturn(liveStreams);

      // when
      List<Stream> result = streamRecommendationService.recommend(List.of("롤", "게임"));

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("검색 태그가 빈 리스트이면 빈 리스트를 반환한다")
    void returnsEmptyListWhenEmptySearchTags() {
      // given
      List<EnrichedStreamDto> liveStreams =
          List.of(EnrichedStreamDtoFixture.lolStream(1), EnrichedStreamDtoFixture.fpsStream(2));
      given(streamRedisStore.findEnrichedStreams()).willReturn(liveStreams);

      // when
      List<Stream> result = streamRecommendationService.recommend(EMPTY_TAGS);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("캐시된 방송이 없으면 빈 리스트를 반환한다")
    void returnsEmptyListWhenNoLiveStreams() {
      // given
      given(streamRedisStore.findEnrichedStreams()).willReturn(List.of());

      // when
      List<Stream> result = streamRecommendationService.recommend(SINGLE_TAG);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("매칭 결과가 5개를 초과하면 상위 5개만 반환한다")
    void returnsMaxFiveStreams() {
      // given
      List<EnrichedStreamDto> liveStreams =
          List.of(
              EnrichedStreamDtoFixture.lolStream(1), // 5점
              EnrichedStreamDtoFixture.lolStream(2), // 5점
              EnrichedStreamDtoFixture.lolStream(3), // 5점
              EnrichedStreamDtoFixture.lolStream(4), // 5점
              EnrichedStreamDtoFixture.lolStream(5), // 5점
              EnrichedStreamDtoFixture.lolStream(6), // 5점 - 제외됨
              EnrichedStreamDtoFixture.lolStream(7) // 5점 - 제외됨
              );
      given(streamRedisStore.findEnrichedStreams()).willReturn(liveStreams);

      // when
      List<Stream> result = streamRecommendationService.recommend(SINGLE_TAG);

      // then
      assertThat(result).hasSize(5);
      assertThat(result.stream().map(Stream::liveId).toList()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("대소문자를 무시하고 매칭한다")
    void matchesIgnoringCase() {
      // given
      List<EnrichedStreamDto> liveStreams =
          List.of(
              EnrichedStreamDtoFixture.withTitle(1, "LOL 챌린저 도전"), // "lol" 제목 매칭
              EnrichedStreamDtoFixture.withTags(
                  2,
                  List.of("lol", "game"), // 소문자 태그
                  List.of("lol", "game", "moba")));
      given(streamRedisStore.findEnrichedStreams()).willReturn(liveStreams);

      // when - 대문자로 검색
      List<Stream> result = streamRecommendationService.recommend(MIXED_CASE_TAGS);

      // then
      assertThat(result).hasSize(2);
      assertThat(result.stream().map(Stream::liveId).toList())
          .containsExactly(1, 2); // "LOL"→"lol", "Game"→"game" 매칭
    }
  }
}
