package com.vatti.chzscout.backend.ai.domain.dto;

/**
 * OpenAI Chat Completion 응답 DTO.
 *
 * @param content AI 응답 내용
 * @param model 사용된 모델명
 * @param promptTokens 프롬프트 토큰 수
 * @param completionTokens 응답 토큰 수
 */
public record ChatCompletionResponse(
    String content, String model, int promptTokens, int completionTokens) {

  public static ChatCompletionResponse of(String content, String model) {
    return new ChatCompletionResponse(content, model, 0, 0);
  }

  public int totalTokens() {
    return promptTokens + completionTokens;
  }
}
