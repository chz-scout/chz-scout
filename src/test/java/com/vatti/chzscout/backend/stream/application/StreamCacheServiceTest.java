package com.vatti.chzscout.backend.stream.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.vatti.chzscout.backend.stream.domain.AllFieldLiveDto;
import com.vatti.chzscout.backend.stream.domain.ChzzkLiveResponse;
import com.vatti.chzscout.backend.stream.fixture.ChzzkLiveResponseFixture;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StreamCacheServiceTest {

  @InjectMocks StreamCacheService streamCacheService;

  @Mock ChzzkApiClient chzzkApiClient;

  @Nested
  @DisplayName("fetchLiveStreams 메서드 테스트")
  class FetchLiveStreamsTest {

    @Test
    @DisplayName("정상적으로 10페이지의 라이브 데이터를 받아 반환한다")
    void fetch200LiveStreamsTest() {
      // given
      List<ChzzkLiveResponse> pages = ChzzkLiveResponseFixture.paginatedResponses(10, 20);

      given(chzzkApiClient.getChzzkLive(null)).willReturn(pages.get(0));
      for (int i = 1; i < 10; i++) {
        String cursor = pages.get(i - 1).page().next();
        given(chzzkApiClient.getChzzkLive(cursor)).willReturn(pages.get(i));
      }

      // when
      List<AllFieldLiveDto> result = streamCacheService.fetchLiveStreams();

      // then
      assertThat(result).hasSize(200);
    }

    @Test
    @DisplayName("API 응답이 비어있으면 빈 리스트를 반환한다")
    void returnEmptyListWhenResponseIsEmpty() {
      // given
      given(chzzkApiClient.getChzzkLive(null)).willReturn(ChzzkLiveResponseFixture.empty());

      // when
      List<AllFieldLiveDto> result = streamCacheService.fetchLiveStreams();

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("3페이지에서 다음 커서가 없으면 3페이지까지만 반환한다")
    void stopAtPageWithoutNextCursor() {
      // given
      given(chzzkApiClient.getChzzkLive(null))
          .willReturn(ChzzkLiveResponseFixture.withNextPage(20, "cursor_1"));
      given(chzzkApiClient.getChzzkLive("cursor_1"))
          .willReturn(ChzzkLiveResponseFixture.withNextPage(20, "cursor_2"));
      given(chzzkApiClient.getChzzkLive("cursor_2"))
          .willReturn(ChzzkLiveResponseFixture.lastPage(20));

      // when
      List<AllFieldLiveDto> result = streamCacheService.fetchLiveStreams();

      // then
      assertThat(result).hasSize(60);
    }
  }
}
