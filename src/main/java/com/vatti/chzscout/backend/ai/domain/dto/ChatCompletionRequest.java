package com.vatti.chzscout.backend.ai.domain.dto;

/**
 * OpenAI Chat Completion 요청 DTO.
 *
 * @param systemPrompt 시스템 프롬프트 (AI의 역할/지시사항)
 * @param userMessage 사용자 메시지
 */
public record ChatCompletionRequest(String systemPrompt, String userMessage) {

  public static ChatCompletionRequest of(String systemPrompt, String userMessage) {
    return new ChatCompletionRequest(systemPrompt, userMessage);
  }
}
