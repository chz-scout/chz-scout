package com.vatti.chzscout.backend.stream.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.vatti.chzscout.backend.stream.application.StreamCacheService;
import com.vatti.chzscout.backend.stream.domain.AllFieldLiveDto;
import com.vatti.chzscout.backend.stream.fixture.AllFieldLiveDtoFixture;
import com.vatti.chzscout.backend.tag.application.TagService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StreamCacheSchedulerTest {
  @Mock private StreamCacheService streamCacheService;
  @Mock private TagService tagService;

  @InjectMocks private StreamCacheScheduler streamCacheScheduler;

  @Nested
  @DisplayName("refreshLiveStreamsCache 메서드 테스트")
  class RefreshLiveStreamsCache {
    @Test
    @DisplayName("정상적으로 api를 호출하고 태그를 추출한다")
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
      verify(tagService).extractAndSaveTag(streams);
    }

    @Test
    @DisplayName("API 호출 중 예외 발생 시 예외를 삼키고 태그 추출은 호출하지 않는다")
    void swallowsExceptionWhenApiCallFails() {
      // given
      given(streamCacheService.refreshLiveStreams()).willThrow(new RuntimeException("API 호출 실패"));

      // when & then - 예외가 전파되지 않음
      assertThatCode(() -> streamCacheScheduler.refreshLiveStreamsCache())
          .doesNotThrowAnyException();

      // tagService는 호출되지 않음
      verify(tagService, never()).extractAndSaveTag(any());
    }

    @Test
    @DisplayName("태그 추출 중 예외 발생 시 예외를 삼킨다")
    void swallowsExceptionWhenTagExtractionFails() {
      // given
      List<AllFieldLiveDto> streams = List.of(AllFieldLiveDtoFixture.create(1));
      given(streamCacheService.refreshLiveStreams()).willReturn(streams);
      willThrow(new RuntimeException("태그 추출 실패")).given(tagService).extractAndSaveTag(any());

      // when & then - 예외가 전파되지 않음
      assertThatCode(() -> streamCacheScheduler.refreshLiveStreamsCache())
          .doesNotThrowAnyException();

      // 두 메서드 모두 호출됨
      verify(streamCacheService).refreshLiveStreams();
      verify(tagService).extractAndSaveTag(streams);
    }
  }
}
