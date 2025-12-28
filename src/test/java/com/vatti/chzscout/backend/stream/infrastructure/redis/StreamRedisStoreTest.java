package com.vatti.chzscout.backend.stream.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.vatti.chzscout.backend.common.config.EmbeddedRedisConfig;
import com.vatti.chzscout.backend.stream.domain.AllFieldLiveDto;
import com.vatti.chzscout.backend.stream.domain.EnrichedStreamDto;
import com.vatti.chzscout.backend.stream.fixture.AllFieldLiveDtoFixture;
import com.vatti.chzscout.backend.stream.fixture.EnrichedStreamDtoFixture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(EmbeddedRedisConfig.class)
class StreamRedisStoreTest {

  @Autowired private StreamRedisStore streamRedisStore;

  @Autowired private StringRedisTemplate stringRedisTemplate;

  private List<EnrichedStreamDto> testStreams;

  @BeforeEach
  void setUp() {
    stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    testStreams = List.of(EnrichedStreamDtoFixture.create(1), EnrichedStreamDtoFixture.create(2));
  }

  @Nested
  @DisplayName("saveEnrichedStreams 메서드")
  class SaveEnrichedStreams {

    @Test
    @DisplayName("Enriched 방송 목록을 저장하면 TTL 10분과 함께 Redis에 저장된다")
    void success() {
      // when
      streamRedisStore.saveEnrichedStreams(testStreams);

      // then
      List<EnrichedStreamDto> result = streamRedisStore.findEnrichedStreams();
      assertThat(result).hasSize(testStreams.size());

      Long expireSeconds = stringRedisTemplate.getExpire("stream:enriched");
      assertThat(expireSeconds)
          .isGreaterThan(0)
          .isLessThanOrEqualTo(Duration.ofMinutes(10).toSeconds());
    }

    @Test
    @DisplayName("빈 리스트를 저장하면 빈 리스트가 저장된다")
    void saveEmptyList() {
      // when
      streamRedisStore.saveEnrichedStreams(List.of());

      // then
      List<EnrichedStreamDto> result = streamRedisStore.findEnrichedStreams();
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("기존 데이터가 있으면 덮어쓴다")
    void overwritesExistingData() {
      // given
      streamRedisStore.saveEnrichedStreams(testStreams);
      List<EnrichedStreamDto> newStreams =
          List.of(
              EnrichedStreamDtoFixture.create(100),
              EnrichedStreamDtoFixture.create(200),
              EnrichedStreamDtoFixture.create(300));

      // when
      streamRedisStore.saveEnrichedStreams(newStreams);

      // then
      List<EnrichedStreamDto> result = streamRedisStore.findEnrichedStreams();
      assertThat(result).hasSize(3);
      assertThat(result.get(0).liveId()).isEqualTo(newStreams.get(0).liveId());
    }
  }

  @Nested
  @DisplayName("findEnrichedStreams 메서드")
  class FindEnrichedStreams {

    @Test
    @DisplayName("저장된 데이터가 있으면 목록을 반환한다")
    void whenExists_returnsList() {
      // given
      streamRedisStore.saveEnrichedStreams(testStreams);

      // when
      List<EnrichedStreamDto> result = streamRedisStore.findEnrichedStreams();

      // then
      assertThat(result).isNotNull().hasSize(2);
      assertThat(result.get(0).liveId()).isEqualTo(testStreams.get(0).liveId());
    }

    @Test
    @DisplayName("저장된 데이터가 없으면 빈 리스트를 반환한다")
    void whenNotExists_returnsEmptyList() {
      // given - @BeforeEach에서 flushAll 완료

      // when
      List<EnrichedStreamDto> result = streamRedisStore.findEnrichedStreams();

      // then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("detectChanges 메서드 테스트")
  class DetectChanges {
    // 0~2: 변경된 stream (old와 current의 태그가 다름)
    // 3~4: 변경 없는 stream (old와 current가 동일)
    // 5~9: 새로운 stream (current에만 존재)
    // 10~14: 종료된 stream (old에만 존재)
    List<AllFieldLiveDto> currentStreams = new ArrayList<>();
    List<AllFieldLiveDto> oldStreams = new ArrayList<>();

    @BeforeEach
    void setUp() {
      // currentStreams: 0~9 (기본 태그)
      for (int i = 0; i < 10; i++) {
        currentStreams.add(AllFieldLiveDtoFixture.create(i));
      }
      // oldStreams 0~2: 다른 태그 (변경 감지용)
      for (int i = 0; i < 3; i++) {
        oldStreams.add(AllFieldLiveDtoFixture.create(i, List.of("이전태그", "old")));
      }
      // oldStreams 3~4: 동일한 태그 (변경 없음)
      for (int i = 3; i < 5; i++) {
        oldStreams.add(AllFieldLiveDtoFixture.create(i));
      }
      // oldStreams 10~14: 종료된 방송
      for (int i = 10; i < 15; i++) {
        oldStreams.add(AllFieldLiveDtoFixture.create(i));
      }
    }

    @Test
    @DisplayName("새로운 stream, 변경된 stream, 종료된 stream을 구분해서 리턴한다")
    void detectChangesSuccess() {
      // given - oldStreams를 먼저 저장하여 이전 해시 생성
      streamRedisStore.detectChanges(oldStreams);

      // when - currentStreams로 변경 감지
      StreamRedisStore.StreamChangeResult result = streamRedisStore.detectChanges(currentStreams);

      // then
      // 신규: channel_5 ~ channel_9 (current에만 있음)
      assertThat(result.newStreams())
          .hasSize(5)
          .containsExactlyInAnyOrder(
              "channel_5", "channel_6", "channel_7", "channel_8", "channel_9");

      // 변경: channel_0 ~ channel_2 (태그가 다름)
      assertThat(result.changedStreams())
          .hasSize(3)
          .containsExactlyInAnyOrder("channel_0", "channel_1", "channel_2");

      // 종료: channel_10 ~ channel_14 (old에만 있음)
      assertThat(result.endedStreams())
          .hasSize(5)
          .containsExactlyInAnyOrder(
              "channel_10", "channel_11", "channel_12", "channel_13", "channel_14");

      // 변경 없음: channel_3, channel_4 (어떤 결과에도 포함되지 않음)
      assertThat(result.newStreams()).doesNotContain("channel_3", "channel_4");
      assertThat(result.changedStreams()).doesNotContain("channel_3", "channel_4");
      assertThat(result.endedStreams()).doesNotContain("channel_3", "channel_4");
    }

    @Test
    @DisplayName("첫 실행 시 모든 방송이 신규로 처리된다")
    void firstRunAllNew() {
      // given - Redis가 비어있는 상태 (@BeforeEach에서 flushAll)

      // when
      StreamRedisStore.StreamChangeResult result = streamRedisStore.detectChanges(currentStreams);

      // then
      assertThat(result.newStreams()).hasSize(10);
      assertThat(result.changedStreams()).isEmpty();
      assertThat(result.endedStreams()).isEmpty();
    }

    @Test
    @DisplayName("빈 currentStreams 입력 시 이전 방송 모두 종료 처리된다")
    void emptyCurrentStreamsAllEnded() {
      // given - 먼저 방송 저장
      streamRedisStore.detectChanges(oldStreams);

      // when - 빈 리스트로 변경 감지
      StreamRedisStore.StreamChangeResult result = streamRedisStore.detectChanges(List.of());

      // then
      assertThat(result.newStreams()).isEmpty();
      assertThat(result.changedStreams()).isEmpty();
      assertThat(result.endedStreams()).hasSize(oldStreams.size());
    }

    @Test
    @DisplayName("모든 방송이 동일하면 결과가 전부 비어있다")
    void allUnchangedEmptyResult() {
      // given - 동일한 방송 목록으로 두 번 호출
      List<AllFieldLiveDto> sameStreams =
          List.of(AllFieldLiveDtoFixture.create(1), AllFieldLiveDtoFixture.create(2));
      streamRedisStore.detectChanges(sameStreams);

      // when - 동일한 목록으로 재호출
      StreamRedisStore.StreamChangeResult result = streamRedisStore.detectChanges(sameStreams);

      // then
      assertThat(result.newStreams()).isEmpty();
      assertThat(result.changedStreams()).isEmpty();
      assertThat(result.endedStreams()).isEmpty();
      assertThat(result.hasChanges()).isFalse();
    }

    @Test
    @DisplayName("hasChanges는 신규 또는 변경이 있으면 true를 반환한다")
    void hasChangesReturnsTrueWhenChangesExist() {
      // given
      streamRedisStore.detectChanges(oldStreams);

      // when
      StreamRedisStore.StreamChangeResult result = streamRedisStore.detectChanges(currentStreams);

      // then - 신규(5개) + 변경(3개)이 있으므로 true
      assertThat(result.hasChanges()).isTrue();
    }

    @Test
    @DisplayName("getAllChangedIds는 신규와 변경된 channelId를 합쳐서 반환한다")
    void getAllChangedIdsReturnsNewAndChanged() {
      // given
      streamRedisStore.detectChanges(oldStreams);

      // when
      StreamRedisStore.StreamChangeResult result = streamRedisStore.detectChanges(currentStreams);

      // then - 신규(5~9) + 변경(0~2) = 8개
      assertThat(result.getAllChangedIds())
          .hasSize(8)
          .containsExactlyInAnyOrder(
              "channel_0",
              "channel_1",
              "channel_2",
              "channel_5",
              "channel_6",
              "channel_7",
              "channel_8",
              "channel_9");
    }

    @Test
    @DisplayName("제목이 변경되면 변경된 방송으로 감지된다")
    void detectsTitleChange() {
      // given
      AllFieldLiveDto originalStream = AllFieldLiveDtoFixture.create(1);
      streamRedisStore.detectChanges(List.of(originalStream));

      // when - 같은 channelId지만 제목이 다른 방송
      AllFieldLiveDto changedStream = AllFieldLiveDtoFixture.withTitle(1, "새로운 방송 제목");
      StreamRedisStore.StreamChangeResult result =
          streamRedisStore.detectChanges(List.of(changedStream));

      // then
      assertThat(result.changedStreams()).containsExactly("channel_1");
      assertThat(result.newStreams()).isEmpty();
    }

    @Test
    @DisplayName("카테고리가 변경되면 변경된 방송으로 감지된다")
    void detectsCategoryChange() {
      // given
      AllFieldLiveDto originalStream = AllFieldLiveDtoFixture.create(1);
      streamRedisStore.detectChanges(List.of(originalStream));

      // when - 같은 channelId지만 카테고리가 다른 방송
      AllFieldLiveDto changedStream =
          AllFieldLiveDtoFixture.withCategory(1, "TALK", "Just Chatting");
      StreamRedisStore.StreamChangeResult result =
          streamRedisStore.detectChanges(List.of(changedStream));

      // then
      assertThat(result.changedStreams()).containsExactly("channel_1");
      assertThat(result.newStreams()).isEmpty();
    }

    @Test
    @DisplayName("종료된 방송만 있으면 hasChanges는 false를 반환한다")
    void hasChangesReturnsFalseWhenOnlyEnded() {
      // given
      streamRedisStore.detectChanges(List.of(AllFieldLiveDtoFixture.create(1)));

      // when - 빈 리스트로 모두 종료 처리
      StreamRedisStore.StreamChangeResult result = streamRedisStore.detectChanges(List.of());

      // then
      assertThat(result.endedStreams()).hasSize(1);
      assertThat(result.hasChanges()).isFalse();
    }
  }
}
