package com.vatti.chzscout.backend.ai.infrastructure;

import java.util.List;

/**
 * 임베딩 벡터 생성 클라이언트 인터페이스.
 *
 * <p>OpenAI Embedding API 호출을 추상화하여 테스트 용이성을 확보합니다.
 */
public interface EmbeddingClient {

  /**
   * 단일 텍스트의 임베딩 벡터를 생성합니다.
   *
   * @param text 임베딩할 텍스트
   * @return 임베딩 벡터 (float 배열)
   */
  float[] embed(String text);

  /**
   * 여러 텍스트의 임베딩 벡터를 배치로 생성합니다.
   *
   * <p>OpenAI API는 한 번에 최대 2048개 텍스트를 처리할 수 있습니다.
   *
   * @param texts 임베딩할 텍스트 목록
   * @return 각 텍스트에 대한 임베딩 벡터 목록 (입력 순서 유지)
   */
  List<float[]> embedBatch(List<String> texts);
}
