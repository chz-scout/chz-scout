package com.vatti.chzscout.backend.stream.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.vatti.chzscout.backend.stream.application.StreamCacheService;
import com.vatti.chzscout.backend.stream.domain.AllFieldLiveDto;
import com.vatti.chzscout.backend.stream.domain.event.StreamCacheRefreshedEvent;
import com.vatti.chzscout.backend.stream.fixture.AllFieldLiveDtoFixture;
import com.vatti.chzscout.backend.tag.application.usecase.TagUseCase;
import java.util.ArrayList;
import java.util.List;
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
      given(streamCacheService.refreshLiveStreams()).willReturn(streams);

      // when
      streamCacheScheduler.refreshLiveStreamsCache();

      // then
      verify(streamCacheService).refreshLiveStreams();
      verify(tagUseCase).extractAndSaveTag(streams);

      // 이벤트 발행 검증
      ArgumentCaptor<StreamCacheRefreshedEvent> eventCaptor =
          ArgumentCaptor.forClass(StreamCacheRefreshedEvent.class);
      verify(eventPublisher).publishEvent(eventCaptor.capture());
    }

    @Test
    @DisplayName("API 호출 중 예외 발생 시 예외를 삼키고 태그 추출과 이벤트 발행을 하지 않는다")
    void swallowsExceptionWhenApiCallFails() {
      // given
      given(streamCacheService.refreshLiveStreams()).willThrow(new RuntimeException("API 호출 실패"));

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
      given(streamCacheService.refreshLiveStreams()).willReturn(streams);
      willThrow(new RuntimeException("태그 추출 실패")).given(tagUseCase).extractAndSaveTag(any());

      // when & then - 예외가 전파되지 않음
      assertThatCode(() -> streamCacheScheduler.refreshLiveStreamsCache())
          .doesNotThrowAnyException();

      // 두 메서드 모두 호출됨
      verify(streamCacheService).refreshLiveStreams();
      verify(tagUseCase).extractAndSaveTag(streams);

      // 이벤트는 발행되지 않음
      verify(eventPublisher, never()).publishEvent(any());
    }
  }
}
