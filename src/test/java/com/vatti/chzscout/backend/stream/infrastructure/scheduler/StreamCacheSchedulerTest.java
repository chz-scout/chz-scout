package com.vatti.chzscout.backend.stream.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.vatti.chzscout.backend.ai.application.AiChatService;
import com.vatti.chzscout.backend.ai.domain.dto.StreamTagResult;
import com.vatti.chzscout.backend.ai.prompt.TagExtractionPrompts;
import com.vatti.chzscout.backend.stream.application.StreamCacheService;
import com.vatti.chzscout.backend.stream.domain.AllFieldLiveDto;
import com.vatti.chzscout.backend.stream.domain.EnrichedStreamDto;
import com.vatti.chzscout.backend.stream.domain.event.StreamCacheRefreshedEvent;
import com.vatti.chzscout.backend.stream.fixture.AllFieldLiveDtoFixture;
import com.vatti.chzscout.backend.stream.fixture.EnrichedStreamDtoFixture;
import com.vatti.chzscout.backend.stream.infrastructure.redis.StreamRedisStore;
import com.vatti.chzscout.backend.tag.application.usecase.TagUseCase;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class StreamCacheSchedulerTest {
  @Mock private StreamCacheService streamCacheService;
  @Mock private TagUseCase tagUseCase;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private StreamRedisStore streamRedisStore;
  @Mock private AiChatService aiChatService;

  @InjectMocks private StreamCacheScheduler streamCacheScheduler;

  @Nested
  @DisplayName("refreshLiveStreamsCache 메서드 테스트")
  class RefreshLiveStreamsCache {
    @Test
    @DisplayName("정상적으로 api를 호출하고 태그를 추출한 후 이벤트를 발행한다")
    void refreshLiveStreamsCache() {
      // given
      List<AllFieldLiveDto> streams = new ArrayList<>();
      for (int i = 0; i < 10; i++) {
        streams.add(AllFieldLiveDtoFixture.create(i));
      }
      List<String> streamChannelIds = streams.stream().map(AllFieldLiveDto::channelId).toList();
      given(streamCacheService.fetchLiveStreams()).willReturn(streams);
      given(streamRedisStore.detectChanges(streams))
          .willReturn(
              new StreamRedisStore.StreamChangeResult(
                  Set.copyOf(streamChannelIds.subList(0, 3)), // newStreams: 0,1,2 (이전에 없던 신규)
                  Set.copyOf(streamChannelIds.subList(3, 5)), // changedStreams: 3,4 (태그 변경됨)
                  Set.of("channel_10", "channel_11") // endedStreams: 10,11 (종료됨)
                  ));

      // 기존 캐시 데이터 - 이전 주기에 저장된 방송들 (3~11)
      // 신규(0,1,2)는 이전에 없었고, 종료(10,11)는 이전에 있었음
      List<EnrichedStreamDto> existingCache = new ArrayList<>();
      for (int i = 3; i < 12; i++) {
        existingCache.add(EnrichedStreamDtoFixture.create(i));
      }
      given(streamRedisStore.findEnrichedStreams()).willReturn(existingCache);

      // AI 태그 추출 결과 - changedIds (0,1,2,3,4)에 대해 반환
      List<StreamTagResult> tagResults = new ArrayList<>();
      for (int i = 0; i < 5; i++) {
        tagResults.add(
            new StreamTagResult(
                "channel_" + i, List.of("롤", "게임"), List.of("롤", "게임", "MOBA", "e스포츠"), 0.9));
      }
      given(aiChatService.extractStreamTagsBatch(any())).willReturn(tagResults);

      // when
      streamCacheScheduler.refreshLiveStreamsCache();

      // then
      // 1. API 호출 검증
      verify(streamCacheService).fetchLiveStreams();

      // 2. 태그 DB 저장 검증
      verify(tagUseCase).extractAndSaveTag(streams);

      // 3. AI 태그 추출 호출 검증 - changedIds(0~4)에 해당하는 5개만 전달
      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<TagExtractionPrompts.StreamInput>> inputCaptor =
          ArgumentCaptor.forClass(List.class);
      verify(aiChatService).extractStreamTagsBatch(inputCaptor.capture());

      List<TagExtractionPrompts.StreamInput> capturedInputs = inputCaptor.getValue();
      assertThat(capturedInputs).hasSize(5);

      List<String> inputChannelIds =
          capturedInputs.stream().map(TagExtractionPrompts.StreamInput::channelId).toList();
      assertThat(inputChannelIds)
          .containsExactlyInAnyOrder(
              "channel_0", "channel_1", "channel_2", "channel_3", "channel_4");

      // 4. Redis 저장 검증 - 최종 10개 방송 저장 (종료된 10,11 제외)
      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<EnrichedStreamDto>> enrichedCaptor = ArgumentCaptor.forClass(List.class);
      verify(streamRedisStore).saveEnrichedStreams(enrichedCaptor.capture());

      List<EnrichedStreamDto> savedStreams = enrichedCaptor.getValue();
      assertThat(savedStreams).hasSize(10);

      // channelId 순서 검증 (0~9)
      List<String> savedChannelIds =
          savedStreams.stream().map(EnrichedStreamDto::channelId).toList();
      assertThat(savedChannelIds)
          .containsExactly(
              "channel_0",
              "channel_1",
              "channel_2",
              "channel_3",
              "channel_4",
              "channel_5",
              "channel_6",
              "channel_7",
              "channel_8",
              "channel_9");

      // 5. 이벤트 발행 검증
      verify(eventPublisher).publishEvent(any(StreamCacheRefreshedEvent.class));
    }

    @Test
    @DisplayName("API 호출 중 예외 발생 시 예외를 삼키고 태그 추출과 이벤트 발행을 하지 않는다")
    void swallowsExceptionWhenApiCallFails() {
      // given
      given(streamCacheService.fetchLiveStreams()).willThrow(new RuntimeException("API 호출 실패"));

      // when & then - 예외가 전파되지 않음
      assertThatCode(() -> streamCacheScheduler.refreshLiveStreamsCache())
          .doesNotThrowAnyException();

      // tagService는 호출되지 않음
      verify(tagUseCase, never()).extractAndSaveTag(any());

      // 이벤트도 발행되지 않음
      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("태그 추출 중 예외 발생 시 예외를 삼키고 이벤트를 발행하지 않는다")
    void swallowsExceptionWhenTagExtractionFails() {
      // given
      List<AllFieldLiveDto> streams = List.of(AllFieldLiveDtoFixture.create(1));
      given(streamCacheService.fetchLiveStreams()).willReturn(streams);
      willThrow(new RuntimeException("태그 추출 실패")).given(tagUseCase).extractAndSaveTag(any());

      // when & then - 예외가 전파되지 않음
      assertThatCode(() -> streamCacheScheduler.refreshLiveStreamsCache())
          .doesNotThrowAnyException();

      // 두 메서드 모두 호출됨
      verify(streamCacheService).fetchLiveStreams();
      verify(tagUseCase).extractAndSaveTag(streams);

      // 이벤트는 발행되지 않음
      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("스트림이 비어있으면 조기 반환하고 후속 로직을 실행하지 않는다")
    void skipsProcessingWhenStreamsEmpty() {
      // given
      given(streamCacheService.fetchLiveStreams()).willReturn(List.of());

      // when
      streamCacheScheduler.refreshLiveStreamsCache();

      // then
      verify(streamCacheService).fetchLiveStreams();

      // 후속 로직 모두 스킵
      verify(tagUseCase, never()).extractAndSaveTag(any());
      verify(streamRedisStore, never()).detectChanges(any());
      verify(aiChatService, never()).extractStreamTagsBatch(any());
      verify(streamRedisStore, never()).saveEnrichedStreams(any());
      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("변경된 방송이 없으면 AI 호출을 스킵하고 기존 캐시를 재사용한다")
    void skipsAiCallWhenNoChanges() {
      // given
      List<AllFieldLiveDto> streams = new ArrayList<>();
      for (int i = 0; i < 5; i++) {
        streams.add(AllFieldLiveDtoFixture.create(i));
      }
      given(streamCacheService.fetchLiveStreams()).willReturn(streams);

      // 변경 없음 - 모든 방송이 이전과 동일
      given(streamRedisStore.detectChanges(streams))
          .willReturn(
              new StreamRedisStore.StreamChangeResult(
                  Set.of(), // newStreams: 없음
                  Set.of(), // changedStreams: 없음
                  Set.of() // endedStreams: 없음
                  ));

      // 기존 캐시 데이터 - 모든 방송이 이전에 저장되어 있음
      List<EnrichedStreamDto> existingCache = new ArrayList<>();
      for (int i = 0; i < 5; i++) {
        existingCache.add(EnrichedStreamDtoFixture.create(i));
      }
      given(streamRedisStore.findEnrichedStreams()).willReturn(existingCache);

      // when
      streamCacheScheduler.refreshLiveStreamsCache();

      // then
      // AI 호출 스킵
      verify(aiChatService, never()).extractStreamTagsBatch(any());

      // Redis 저장 검증 - 기존 캐시 재사용
      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<EnrichedStreamDto>> enrichedCaptor = ArgumentCaptor.forClass(List.class);
      verify(streamRedisStore).saveEnrichedStreams(enrichedCaptor.capture());

      List<EnrichedStreamDto> savedStreams = enrichedCaptor.getValue();
      assertThat(savedStreams).hasSize(5);

      // 이벤트 발행됨
      verify(eventPublisher).publishEvent(any(StreamCacheRefreshedEvent.class));
    }

    @Test
    @DisplayName("AI 서비스 예외 발생 시 예외를 삼키고 이벤트를 발행하지 않는다")
    void swallowsExceptionWhenAiServiceFails() {
      // given
      List<AllFieldLiveDto> streams = List.of(AllFieldLiveDtoFixture.create(1));
      given(streamCacheService.fetchLiveStreams()).willReturn(streams);
      given(streamRedisStore.detectChanges(streams))
          .willReturn(
              new StreamRedisStore.StreamChangeResult(
                  Set.of("channel_1"), // 신규 방송
                  Set.of(),
                  Set.of()));
      given(streamRedisStore.findEnrichedStreams()).willReturn(List.of());
      willThrow(new RuntimeException("AI 서비스 오류"))
          .given(aiChatService)
          .extractStreamTagsBatch(any());

      // when & then - 예외가 전파되지 않음
      assertThatCode(() -> streamCacheScheduler.refreshLiveStreamsCache())
          .doesNotThrowAnyException();

      // AI 호출 시도됨
      verify(aiChatService).extractStreamTagsBatch(any());

      // Redis 저장 및 이벤트 발행되지 않음
      verify(streamRedisStore, never()).saveEnrichedStreams(any());
      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Redis 저장 예외 발생 시 예외를 삼키고 이벤트를 발행하지 않는다")
    void swallowsExceptionWhenRedisSaveFails() {
      // given
      List<AllFieldLiveDto> streams = List.of(AllFieldLiveDtoFixture.create(1));
      given(streamCacheService.fetchLiveStreams()).willReturn(streams);
      given(streamRedisStore.detectChanges(streams))
          .willReturn(
              new StreamRedisStore.StreamChangeResult(Set.of("channel_1"), Set.of(), Set.of()));
      given(streamRedisStore.findEnrichedStreams()).willReturn(List.of());
      given(aiChatService.extractStreamTagsBatch(any()))
          .willReturn(
              List.of(new StreamTagResult("channel_1", List.of("게임"), List.of("게임", "롤"), 0.9)));
      willThrow(new RuntimeException("Redis 저장 오류"))
          .given(streamRedisStore)
          .saveEnrichedStreams(any());

      // when & then - 예외가 전파되지 않음
      assertThatCode(() -> streamCacheScheduler.refreshLiveStreamsCache())
          .doesNotThrowAnyException();

      // Redis 저장 시도됨
      verify(streamRedisStore).saveEnrichedStreams(any());

      // 이벤트 발행되지 않음
      verify(eventPublisher, never()).publishEvent(any());
    }
  }
}
