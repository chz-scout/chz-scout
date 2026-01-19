package com.vatti.chzscout.backend.ai.infrastructure;

import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.Embedding;
import com.openai.models.embeddings.EmbeddingCreateParams;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OpenAiEmbeddingClientImpl implements EmbeddingClient {

  private final OpenAIClient openAIClient;
  private final String model;
  private final int dimensions;

  public OpenAiEmbeddingClientImpl(
      OpenAIClient openAIClient,
      @Value("${embedding.model}") String model,
      @Value("${embedding.dimensions}") int dimensions) {
    this.openAIClient = openAIClient;
    this.model = model;
    this.dimensions = dimensions;
  }

  @Override
  public float[] embed(String text) {
    log.debug("임베딩 생성 - model: {}, text length: {}", model, text.length());

    EmbeddingCreateParams params =
        EmbeddingCreateParams.builder().model(model).dimensions(dimensions).input(text).build();

    List<Float> vector =
        openAIClient.embeddings().create(params).data().stream()
            .findFirst()
            .map(Embedding::embedding)
            .orElseThrow(() -> new RuntimeException("임베딩 응답이 비어있습니다"));

    return toFloatArray(vector);
  }

  @Override
  public List<float[]> embedBatch(List<String> texts) {
    if (texts.isEmpty()) {
      return List.of();
    }

    log.debug("배치 임베딩 생성 - model: {}, count: {}", model, texts.size());

    EmbeddingCreateParams params =
        EmbeddingCreateParams.builder()
            .model(model)
            .dimensions(dimensions)
            .inputOfArrayOfStrings(texts)
            .build();

    // index 순서로 정렬하여 입력 순서와 일치시킴
    return openAIClient.embeddings().create(params).data().stream()
        .sorted((a, b) -> Long.compare(a.index(), b.index()))
        .map(embedding -> toFloatArray(embedding.embedding()))
        .toList();
  }

  private float[] toFloatArray(List<Float> floatList) {
    float[] floats = new float[floatList.size()];
    for (int i = 0; i < floatList.size(); i++) {
      floats[i] = floatList.get(i);
    }
    return floats;
  }
}
