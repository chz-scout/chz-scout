package com.vatti.chzscout.backend.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.vatti.chzscout.backend.ai.domain.dto.StreamEmbeddingWithSimilarity;
import com.vatti.chzscout.backend.ai.domain.entity.StreamEmbedding;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * pgvector 기반 StreamEmbeddingRepository 통합 테스트.
 *
 * <p>Testcontainers로 pgvector 확장이 포함된 PostgreSQL을 실행합니다. Docker가 실행 중이어야 테스트가 가능합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StreamEmbeddingRepositoryIntegrationTest {

  @Container
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer("pgvector/pgvector:pg16")
          .withDatabaseName("testdb")
          .withUsername("test")
          .withPassword("test")
          .withInitScript("db/init-pgvector.sql");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
  }

  @Autowired private StreamEmbeddingRepository streamEmbeddingRepository;

  /** 테스트용 1536차원 벡터 생성 */
  private float[] createTestVector(float baseValue) {
    float[] vector = new float[1536];
    for (int i = 0; i < 1536; i++) {
      vector[i] = baseValue + (i * 0.0001f);
    }
    return vector;
  }

  /** float 배열을 pgvector 문자열로 변환 */
  private String toVectorString(float[] vector) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < vector.length; i++) {
      if (i > 0) sb.append(",");
      sb.append(vector[i]);
    }
    sb.append("]");
    return sb.toString();
  }

  @Nested
  @DisplayName("findSimilarEmbeddings 메서드 테스트")
  class FindSimilarEmbeddings {

    @BeforeEach
    void setUp() {
      streamEmbeddingRepository.deleteAll();
    }

    @Test
    @DisplayName("유사도가 높은 순서로 결과를 반환한다")
    void returnsSimilarEmbeddingsOrderedBySimilarity() {
      // given - 3개의 임베딩 저장
      float[] baseVector = createTestVector(0.1f);
      float[] similarVector = createTestVector(0.11f); // baseVector와 유사
      float[] differentVector = createTestVector(0.9f); // baseVector와 다름

      streamEmbeddingRepository.saveAndFlush(
          StreamEmbedding.create("channel_similar", "롤 방송", similarVector));
      streamEmbeddingRepository.saveAndFlush(
          StreamEmbedding.create("channel_different", "요리 방송", differentVector));
      streamEmbeddingRepository.saveAndFlush(
          StreamEmbedding.create("channel_base", "롤 e스포츠", baseVector));

      // when - baseVector와 유사한 임베딩 검색
      String queryVector = toVectorString(baseVector);
      List<StreamEmbeddingWithSimilarity> results =
          streamEmbeddingRepository.findSimilarEmbeddings(queryVector, 3);

      // then
      assertThat(results).hasSize(3);
      // 자기 자신이 가장 유사 (similarity ≈ 1.0)
      assertThat(results.get(0).getChannelId()).isEqualTo("channel_base");
      assertThat(results.get(0).getSimilarity())
          .isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
      // 비슷한 벡터가 두 번째
      assertThat(results.get(1).getChannelId()).isEqualTo("channel_similar");
      // 다른 벡터가 마지막
      assertThat(results.get(2).getChannelId()).isEqualTo("channel_different");
    }

    @Test
    @DisplayName("limit 개수만큼만 결과를 반환한다")
    void respectsLimitParameter() {
      // given - 5개 임베딩 저장
      for (int i = 0; i < 5; i++) {
        streamEmbeddingRepository.save(
            StreamEmbedding.create("channel_" + i, "방송 " + i, createTestVector(0.1f * i)));
      }

      // when
      String queryVector = toVectorString(createTestVector(0.1f));
      List<StreamEmbeddingWithSimilarity> results =
          streamEmbeddingRepository.findSimilarEmbeddings(queryVector, 3);

      // then
      assertThat(results).hasSize(3);
    }

    @Test
    @DisplayName("저장된 임베딩이 없으면 빈 리스트를 반환한다")
    void returnsEmptyListWhenNoEmbeddings() {
      // when
      String queryVector = toVectorString(createTestVector(0.1f));
      List<StreamEmbeddingWithSimilarity> results =
          streamEmbeddingRepository.findSimilarEmbeddings(queryVector, 5);

      // then
      assertThat(results).isEmpty();
    }
  }

  @Nested
  @DisplayName("findPersonalizedRecommendations 메서드 테스트")
  class FindPersonalizedRecommendations {

    @BeforeEach
    void setUp() {
      streamEmbeddingRepository.deleteAll();
    }

    @Test
    @DisplayName("쿼리와 선호 벡터의 가중치 혼합 점수로 정렬한다")
    void sortsbyWeightedScore() {
      // given - 3개의 임베딩 저장
      float[] queryLikeVector = createTestVector(0.1f); // 쿼리와 유사
      float[] preferenceLikeVector = createTestVector(0.5f); // 선호와 유사
      float[] balancedVector = createTestVector(0.3f); // 중간

      streamEmbeddingRepository.saveAndFlush(
          StreamEmbedding.create("channel_query", "쿼리 유사 방송", queryLikeVector));
      streamEmbeddingRepository.saveAndFlush(
          StreamEmbedding.create("channel_pref", "선호 유사 방송", preferenceLikeVector));
      streamEmbeddingRepository.saveAndFlush(
          StreamEmbedding.create("channel_balanced", "균형 방송", balancedVector));

      String queryEmbedding = toVectorString(createTestVector(0.1f));
      String preferenceEmbedding = toVectorString(createTestVector(0.5f));

      // when - 쿼리 90%, 선호 10% 가중치
      List<StreamEmbeddingWithSimilarity> results =
          streamEmbeddingRepository.findPersonalizedRecommendations(
              queryEmbedding, preferenceEmbedding, 0.0, 0.1, 3);

      // then - 쿼리 가중치가 높으므로 쿼리 유사 채널이 1위
      assertThat(results).hasSize(3);
      assertThat(results.get(0).getChannelId()).isEqualTo("channel_query");
    }

    @Test
    @DisplayName("선호 가중치가 높으면 선호 유사 방송이 상위에 온다")
    void highPreferenceWeightFavorsPreferenceSimilar() {
      // given
      float[] queryLikeVector = createTestVector(0.1f);
      float[] preferenceLikeVector = createTestVector(0.5f);

      streamEmbeddingRepository.saveAndFlush(
          StreamEmbedding.create("channel_query", "쿼리 유사", queryLikeVector));
      streamEmbeddingRepository.saveAndFlush(
          StreamEmbedding.create("channel_pref", "선호 유사", preferenceLikeVector));

      String queryEmbedding = toVectorString(createTestVector(0.1f));
      String preferenceEmbedding = toVectorString(createTestVector(0.5f));

      // when - 쿼리 10%, 선호 90% 가중치
      List<StreamEmbeddingWithSimilarity> results =
          streamEmbeddingRepository.findPersonalizedRecommendations(
              queryEmbedding, preferenceEmbedding, 0.0, 0.9, 2);

      // then - 선호 가중치가 높으므로 선호 유사 채널이 1위
      assertThat(results).hasSize(2);
      assertThat(results.get(0).getChannelId()).isEqualTo("channel_pref");
    }

    @Test
    @DisplayName("threshold 미만의 쿼리 유사도는 필터링된다")
    void filtersResultsBelowThreshold() {
      // given
      float[] similarVector = createTestVector(0.1f);
      float[] differentVector = createTestVector(0.9f);

      streamEmbeddingRepository.saveAndFlush(
          StreamEmbedding.create("channel_similar", "유사 방송", similarVector));
      streamEmbeddingRepository.saveAndFlush(
          StreamEmbedding.create("channel_different", "다른 방송", differentVector));

      String queryEmbedding = toVectorString(createTestVector(0.1f));
      String preferenceEmbedding = toVectorString(createTestVector(0.5f));

      // when - 높은 threshold 설정
      List<StreamEmbeddingWithSimilarity> results =
          streamEmbeddingRepository.findPersonalizedRecommendations(
              queryEmbedding, preferenceEmbedding, 0.99, 0.3, 5);

      // then - 자기 자신(유사도 1.0)만 threshold 통과
      assertThat(results).hasSize(1);
      assertThat(results.get(0).getChannelId()).isEqualTo("channel_similar");
    }

    @Test
    @DisplayName("threshold가 0이면 모든 결과를 반환한다")
    void zeroThresholdReturnsAll() {
      // given
      for (int i = 0; i < 5; i++) {
        streamEmbeddingRepository.saveAndFlush(
            StreamEmbedding.create("channel_" + i, "방송 " + i, createTestVector(0.1f * i)));
      }

      String queryEmbedding = toVectorString(createTestVector(0.1f));
      String preferenceEmbedding = toVectorString(createTestVector(0.5f));

      // when
      List<StreamEmbeddingWithSimilarity> results =
          streamEmbeddingRepository.findPersonalizedRecommendations(
              queryEmbedding, preferenceEmbedding, 0.0, 0.3, 10);

      // then
      assertThat(results).hasSize(5);
    }

    @Test
    @DisplayName("limit 개수만큼만 결과를 반환한다")
    void respectsLimitParameter() {
      // given
      for (int i = 0; i < 10; i++) {
        streamEmbeddingRepository.saveAndFlush(
            StreamEmbedding.create("channel_" + i, "방송 " + i, createTestVector(0.1f)));
      }

      String queryEmbedding = toVectorString(createTestVector(0.1f));
      String preferenceEmbedding = toVectorString(createTestVector(0.5f));

      // when
      List<StreamEmbeddingWithSimilarity> results =
          streamEmbeddingRepository.findPersonalizedRecommendations(
              queryEmbedding, preferenceEmbedding, 0.0, 0.3, 3);

      // then
      assertThat(results).hasSize(3);
    }
  }

  @Nested
  @DisplayName("기본 CRUD 테스트")
  class BasicCrud {

    @BeforeEach
    void setUp() {
      streamEmbeddingRepository.deleteAll();
    }

    @Test
    @DisplayName("StreamEmbedding을 저장하고 조회할 수 있다")
    void saveAndFind() {
      // given
      float[] embedding = createTestVector(0.5f);
      StreamEmbedding entity = StreamEmbedding.create("test_channel", "테스트 방송 롤", embedding);

      // when
      streamEmbeddingRepository.save(entity);
      StreamEmbedding found = streamEmbeddingRepository.findById("test_channel").orElse(null);

      // then
      assertThat(found).isNotNull();
      assertThat(found.getChannelId()).isEqualTo("test_channel");
      assertThat(found.getEmbeddingText()).isEqualTo("테스트 방송 롤");
      assertThat(found.getEmbedding()).hasSize(1536);
    }

    @Test
    @DisplayName("같은 channelId로 저장하면 업데이트된다")
    void updateExisting() {
      // given
      StreamEmbedding original =
          StreamEmbedding.create("channel_1", "원본 텍스트", createTestVector(0.1f));
      streamEmbeddingRepository.save(original);

      // when
      StreamEmbedding found = streamEmbeddingRepository.findById("channel_1").orElseThrow();
      found.updateEmbedding("변경된 텍스트", createTestVector(0.9f));
      streamEmbeddingRepository.save(found);

      // then
      StreamEmbedding updated = streamEmbeddingRepository.findById("channel_1").orElseThrow();
      assertThat(updated.getEmbeddingText()).isEqualTo("변경된 텍스트");
    }
  }
}
