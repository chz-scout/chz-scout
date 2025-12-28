package com.vatti.chzscout.backend.ai.infrastructure;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.StructuredChatCompletionCreateParams;
import com.vatti.chzscout.backend.ai.config.OpenAiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OpenAI Chat Completion API 클라이언트 구현체.
 *
 * <p>OpenAI SDK를 사용하여 실제 API 호출을 수행합니다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OpenAiChatClientImpl implements OpenAiChatClient {

  private final OpenAIClient openAIClient;
  private final OpenAiProperties properties;

  @Override
  public <T> T chatWithStructuredOutput(
      String systemPrompt, String userMessage, Class<T> responseType) {
    log.debug(
        "OpenAI API 호출 - model: {}, responseType: {}",
        properties.getModel(),
        responseType.getSimpleName());

    StructuredChatCompletionCreateParams<T> params =
        ChatCompletionCreateParams.builder()
            .model(properties.getModel())
            .addSystemMessage(systemPrompt)
            .addUserMessage(userMessage)
            .responseFormat(responseType)
            .build();

    return openAIClient.chat().completions().create(params).choices().stream()
        .findFirst()
        .flatMap(choice -> choice.message().content())
        .orElseThrow(() -> new RuntimeException("AI 응답이 비어있습니다"));
  }
}
