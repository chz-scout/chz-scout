package com.vatti.chzscout.backend.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.vatti.chzscout.backend.ai.domain.dto.MemberEmbeddingVectorResult;
import com.vatti.chzscout.backend.ai.domain.dto.StreamEmbeddingWithSimilarity;
import com.vatti.chzscout.backend.ai.infrastructure.EmbeddingClient;
import com.vatti.chzscout.backend.ai.infrastructure.StreamEmbeddingRepository;
import com.vatti.chzscout.backend.member.domain.entity.Member;
import com.vatti.chzscout.backend.stream.domain.EnrichedStreamDto;
import com.vatti.chzscout.backend.stream.domain.Stream;
import com.vatti.chzscout.backend.stream.fixture.EnrichedStreamDtoFixture;
import com.vatti.chzscout.backend.stream.infrastructure.redis.StreamRedisStore;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VectorRecommendServiceTest {

  @Mock private StreamEmbeddingRepository streamEmbeddingRepository;
  @Mock private EmbeddingClient embeddingClient;
  @Mock private EmbeddingService embeddingService;
  @Mock private StreamRedisStore streamRedisStore;

  @InjectMocks private VectorRecommendService vectorRecommendService;

  @BeforeEach
  void setUp() {
    // @Value 필드 기본값 설정
    ReflectionTestUtils.setField(vectorRecommendService, "similarityThreshold", 0.23);
    ReflectionTestUtils.setField(vectorRecommendService, "preferenceWeight", 0.1);
  }

  private float[] createTestEmbedding() {
    float[] embedding = new float[1536];
    for (int i = 0; i < 1536; i++) {
      embedding[i] = 0.1f;
    }
    return embedding;
  }

  @Nested
  @DisplayName("recommend 메서드 테스트")
  class Recommend {

    @Test
    @DisplayName("null 메시지면 빈 리스트를 반환한다")
    void returnsEmptyListForNullMessage() {
      // when
      List<Stream> result = vectorRecommendService.recommend(null, 5);

      // then
      assertThat(result).isEmpty();
      verify(embeddingClient, never()).embed(anyString());
    }

    @Test
    @DisplayName("빈 메시지면 빈 리스트를 반환한다")
    void returnsEmptyListForBlankMessage() {
      // when
      List<Stream> result = vectorRecommendService.recommend("   ", 5);

      // then
      assertThat(result).isEmpty();
      verify(embeddingClient, never()).embed(anyString());
    }

    @Test
    @DisplayName("limit이 0 이하면 기본값 5를 사용한다")
    void usesDefaultLimitWhenZeroOrNegative() {
      // given
      float[] embedding = createTestEmbedding();
      given(embeddingClient.embed("롤 방송")).willReturn(embedding);
      given(streamEmbeddingRepository.findSimilarEmbeddings(anyString(), anyInt()))
          .willReturn(List.of());

      // when
      vectorRecommendService.recommend("롤 방송", 0);

      // then
      verify(streamEmbeddingRepository)
          .findSimilarEmbeddings(anyString(), org.mockito.ArgumentMatchers.eq(5));
    }

    @Test
    @DisplayName("유사한 임베딩이 없으면 빈 리스트를 반환한다")
    void returnsEmptyListWhenNoSimilarEmbeddings() {
      // given
      float[] embedding = createTestEmbedding();
      given(embeddingClient.embed("롤 방송")).willReturn(embedding);
      given(streamEmbeddingRepository.findSimilarEmbeddings(anyString(), anyInt()))
          .willReturn(List.of());

      // when
      List<Stream> result = vectorRecommendService.recommend("롤 방송", 5);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("유사한 방송을 찾아 Stream 목록으로 반환한다")
    void returnsStreamListFromSimilarEmbeddings() {
      // given
      float[] embedding = createTestEmbedding();
      given(embeddingClient.embed("롤 방송")).willReturn(embedding);

      List<StreamEmbeddingWithSimilarity> similarEmbeddings =
          List.of(
              new TestStreamEmbeddingWithSimilarity(
                  "channel_1", "롤 방송 1", LocalDateTime.now(), 0.95),
              new TestStreamEmbeddingWithSimilarity(
                  "channel_2", "롤 방송 2", LocalDateTime.now(), 0.85));
      given(streamEmbeddingRepository.findSimilarEmbeddings(anyString(), anyInt()))
          .willReturn(similarEmbeddings);

      List<EnrichedStreamDto> enrichedStreams =
          List.of(
              EnrichedStreamDtoFixture.createWithChannelId("channel_1"),
              EnrichedStreamDtoFixture.createWithChannelId("channel_2"),
              EnrichedStreamDtoFixture.createWithChannelId("channel_3"));
      given(streamRedisStore.findEnrichedStreams()).willReturn(enrichedStreams);

      // when
      List<Stream> result = vectorRecommendService.recommend("롤 방송", 5);

      // then
      assertThat(result).hasSize(2);
      assertThat(result.get(0).channelId()).isEqualTo("channel_1");
      assertThat(result.get(1).channelId()).isEqualTo("channel_2");
    }

    @Test
    @DisplayName("Redis에 없는 채널은 결과에서 제외된다")
    void excludesChannelsNotInRedis() {
      // given
      float[] embedding = createTestEmbedding();
      given(embeddingClient.embed("롤 방송")).willReturn(embedding);

      List<StreamEmbeddingWithSimilarity> similarEmbeddings =
          List.of(
              new TestStreamEmbeddingWithSimilarity(
                  "channel_1", "롤 방송 1", LocalDateTime.now(), 0.95),
              new TestStreamEmbeddingWithSimilarity(
                  "channel_missing", "방송 종료됨", LocalDateTime.now(), 0.85));
      given(streamEmbeddingRepository.findSimilarEmbeddings(anyString(), anyInt()))
          .willReturn(similarEmbeddings);

      // Redis에는 channel_1만 있음
      List<EnrichedStreamDto> enrichedStreams =
          List.of(EnrichedStreamDtoFixture.createWithChannelId("channel_1"));
      given(streamRedisStore.findEnrichedStreams()).willReturn(enrichedStreams);

      // when
      List<Stream> result = vectorRecommendService.recommend("롤 방송", 5);

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).channelId()).isEqualTo("channel_1");
    }

    @Test
    @DisplayName("유사도 순서를 유지하며 결과를 반환한다")
    void maintainsSimilarityOrder() {
      // given
      float[] embedding = createTestEmbedding();
      given(embeddingClient.embed("롤 방송")).willReturn(embedding);

      // 유사도 순: channel_2 > channel_1 > channel_3
      List<StreamEmbeddingWithSimilarity> similarEmbeddings =
          List.of(
              new TestStreamEmbeddingWithSimilarity("channel_2", "방송 2", LocalDateTime.now(), 0.99),
              new TestStreamEmbeddingWithSimilarity("channel_1", "방송 1", LocalDateTime.now(), 0.85),
              new TestStreamEmbeddingWithSimilarity(
                  "channel_3", "방송 3", LocalDateTime.now(), 0.70));
      given(streamEmbeddingRepository.findSimilarEmbeddings(anyString(), anyInt()))
          .willReturn(similarEmbeddings);

      List<EnrichedStreamDto> enrichedStreams =
          List.of(
              EnrichedStreamDtoFixture.createWithChannelId("channel_1"),
              EnrichedStreamDtoFixture.createWithChannelId("channel_2"),
              EnrichedStreamDtoFixture.createWithChannelId("channel_3"));
      given(streamRedisStore.findEnrichedStreams()).willReturn(enrichedStreams);

      // when
      List<Stream> result = vectorRecommendService.recommend("롤 방송", 5);

      // then
      assertThat(result).hasSize(3);
      assertThat(result.get(0).channelId()).isEqualTo("channel_2"); // 최고 유사도
      assertThat(result.get(1).channelId()).isEqualTo("channel_1");
      assertThat(result.get(2).channelId()).isEqualTo("channel_3"); // 최저 유사도
    }
  }

  @Nested
  @DisplayName("개인화 recommend 메서드 테스트")
  class PersonalizedRecommend {

    @Mock private Member member;

    @Test
    @DisplayName("null 메시지면 빈 리스트를 반환한다")
    void returnsEmptyListForNullMessage() {
      // when
      List<Stream> result = vectorRecommendService.recommend(null, member, 5);

      // then
      assertThat(result).isEmpty();
      verify(embeddingClient, never()).embed(anyString());
    }

    @Test
    @DisplayName("빈 메시지면 빈 리스트를 반환한다")
    void returnsEmptyListForBlankMessage() {
      // when
      List<Stream> result = vectorRecommendService.recommend("   ", member, 5);

      // then
      assertThat(result).isEmpty();
      verify(embeddingClient, never()).embed(anyString());
    }

    @Test
    @DisplayName("선호 벡터가 있으면 개인화 추천을 수행한다")
    void usesPersonalizedRecommendationWhenPreferenceExists() {
      // given
      float[] queryEmbedding = createTestEmbedding();
      float[] preferenceEmbedding = createTestEmbedding();

      given(embeddingClient.embed("롤 방송")).willReturn(queryEmbedding);
      given(embeddingService.createMemberEmbeddingVector(member))
          .willReturn(new MemberEmbeddingVectorResult("test-uuid", preferenceEmbedding));

      List<StreamEmbeddingWithSimilarity> similarEmbeddings =
          List.of(
              new TestStreamEmbeddingWithSimilarity(
                  "channel_1", "롤 방송", LocalDateTime.now(), 0.95));
      given(
              streamEmbeddingRepository.findPersonalizedRecommendations(
                  anyString(), anyString(), anyDouble(), anyDouble(), anyInt()))
          .willReturn(similarEmbeddings);

      List<EnrichedStreamDto> enrichedStreams =
          List.of(EnrichedStreamDtoFixture.createWithChannelId("channel_1"));
      given(streamRedisStore.findEnrichedStreams()).willReturn(enrichedStreams);

      // when
      List<Stream> result = vectorRecommendService.recommend("롤 방송", member, 5);

      // then
      assertThat(result).hasSize(1);
      verify(streamEmbeddingRepository)
          .findPersonalizedRecommendations(anyString(), anyString(), eq(0.23), eq(0.1), eq(5));
    }

    @Test
    @DisplayName("선호 벡터가 없으면 일반 추천으로 fallback한다")
    void fallsBackToBasicRecommendationWhenNoPreference() {
      // given
      float[] queryEmbedding = createTestEmbedding();

      given(embeddingClient.embed("롤 방송")).willReturn(queryEmbedding);
      given(embeddingService.createMemberEmbeddingVector(member)).willReturn(null);

      List<StreamEmbeddingWithSimilarity> similarEmbeddings =
          List.of(
              new TestStreamEmbeddingWithSimilarity(
                  "channel_1", "롤 방송", LocalDateTime.now(), 0.95));
      given(streamEmbeddingRepository.findSimilarEmbeddings(anyString(), anyInt()))
          .willReturn(similarEmbeddings);

      List<EnrichedStreamDto> enrichedStreams =
          List.of(EnrichedStreamDtoFixture.createWithChannelId("channel_1"));
      given(streamRedisStore.findEnrichedStreams()).willReturn(enrichedStreams);

      // when
      List<Stream> result = vectorRecommendService.recommend("롤 방송", member, 5);

      // then
      assertThat(result).hasSize(1);
      // 개인화 추천이 아닌 일반 추천 호출 확인
      verify(streamEmbeddingRepository).findSimilarEmbeddings(anyString(), eq(5));
      verify(streamEmbeddingRepository, never())
          .findPersonalizedRecommendations(
              anyString(), anyString(), anyDouble(), anyDouble(), anyInt());
    }

    @Test
    @DisplayName("Redis에 없는 채널은 결과에서 제외된다")
    void excludesChannelsNotInRedis() {
      // given
      float[] queryEmbedding = createTestEmbedding();
      float[] preferenceEmbedding = createTestEmbedding();

      given(embeddingClient.embed("롤 방송")).willReturn(queryEmbedding);
      given(embeddingService.createMemberEmbeddingVector(member))
          .willReturn(new MemberEmbeddingVectorResult("test-uuid", preferenceEmbedding));

      List<StreamEmbeddingWithSimilarity> similarEmbeddings =
          List.of(
              new TestStreamEmbeddingWithSimilarity("channel_1", "방송 1", LocalDateTime.now(), 0.95),
              new TestStreamEmbeddingWithSimilarity(
                  "channel_missing", "종료된 방송", LocalDateTime.now(), 0.85));
      given(
              streamEmbeddingRepository.findPersonalizedRecommendations(
                  anyString(), anyString(), anyDouble(), anyDouble(), anyInt()))
          .willReturn(similarEmbeddings);

      // Redis에는 channel_1만 있음
      List<EnrichedStreamDto> enrichedStreams =
          List.of(EnrichedStreamDtoFixture.createWithChannelId("channel_1"));
      given(streamRedisStore.findEnrichedStreams()).willReturn(enrichedStreams);

      // when
      List<Stream> result = vectorRecommendService.recommend("롤 방송", member, 5);

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).channelId()).isEqualTo("channel_1");
    }

    @Test
    @DisplayName("유사한 임베딩이 없으면 빈 리스트를 반환한다")
    void returnsEmptyListWhenNoSimilarEmbeddings() {
      // given
      float[] queryEmbedding = createTestEmbedding();
      float[] preferenceEmbedding = createTestEmbedding();

      given(embeddingClient.embed("희귀한 방송")).willReturn(queryEmbedding);
      given(embeddingService.createMemberEmbeddingVector(member))
          .willReturn(new MemberEmbeddingVectorResult("test-uuid", preferenceEmbedding));
      given(
              streamEmbeddingRepository.findPersonalizedRecommendations(
                  anyString(), anyString(), anyDouble(), anyDouble(), anyInt()))
          .willReturn(List.of());

      // when
      List<Stream> result = vectorRecommendService.recommend("희귀한 방송", member, 5);

      // then
      assertThat(result).isEmpty();
    }
  }

  /** 테스트용 StreamEmbeddingWithSimilarity 구현체. */
  private static class TestStreamEmbeddingWithSimilarity implements StreamEmbeddingWithSimilarity {
    private final String channelId;
    private final String embeddingText;
    private final LocalDateTime updatedAt;
    private final double similarity;

    TestStreamEmbeddingWithSimilarity(
        String channelId, String embeddingText, LocalDateTime updatedAt, double similarity) {
      this.channelId = channelId;
      this.embeddingText = embeddingText;
      this.updatedAt = updatedAt;
      this.similarity = similarity;
    }

    @Override
    public String getChannelId() {
      return channelId;
    }

    @Override
    public String getEmbeddingText() {
      return embeddingText;
    }

    @Override
    public LocalDateTime getUpdatedAt() {
      return updatedAt;
    }

    @Override
    public Double getSimilarity() {
      return similarity;
    }
  }
}
