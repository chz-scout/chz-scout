package com.vatti.chzscout.backend.ai.infrastructure;

/**
 * OpenAI Chat Completion API 클라이언트 인터페이스.
 *
 * <p>OpenAI SDK의 복잡한 체이닝을 추상화하여 테스트 용이성을 확보합니다.
 */
public interface OpenAiChatClient {

  /**
   * Structured Output을 사용하여 AI 응답을 받습니다.
   *
   * @param systemPrompt 시스템 프롬프트
   * @param userMessage 사용자 메시지
   * @param responseType 응답 타입 클래스
   * @param <T> 응답 타입
   * @return 파싱된 응답 객체
   */
  <T> T chatWithStructuredOutput(String systemPrompt, String userMessage, Class<T> responseType);
}
