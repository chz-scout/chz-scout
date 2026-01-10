package com.vatti.chzscout.backend.ai.performance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatti.chzscout.backend.ai.domain.dto.UserMessageAnalysisResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** OpenAI API 응답 Fixture. */
public class OpenAiResponseFixture {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  // ==================== 공개 API ====================

  /** 추천 요청에 대한 기본 응답을 생성합니다. */
  public static String recommendationResponse() {
    UserMessageAnalysisResult result =
        new UserMessageAnalysisResult(
            "recommendation", List.of("게임", "롤", "e스포츠"), List.of("롤", "게임"), null);
    return chatCompletionResponse(result);
  }

  /** 검색 요청에 대한 기본 응답을 생성합니다. */
  public static String searchResponse() {
    UserMessageAnalysisResult result =
        new UserMessageAnalysisResult("search", List.of(), List.of("페이커"), "페이커 방송을 검색합니다.");
    return chatCompletionResponse(result);
  }

  /** Chat Completion API 응답을 생성합니다. */
  public static String chatCompletionResponse(UserMessageAnalysisResult result) {
    String contentJson = buildContentJson(result);
    Map<String, Object> response = buildChatCompletion(contentJson);
    return toJson(response);
  }

  // ==================== 응답 구조 빌더 ====================

  /** 전체 Chat Completion 응답 구조를 빌드합니다. */
  private static Map<String, Object> buildChatCompletion(String contentJson) {
    Map<String, Object> response = new HashMap<>();
    response.put("id", "chatcmpl-test-" + System.currentTimeMillis());
    response.put("object", "chat.completion");
    response.put("created", System.currentTimeMillis() / 1000);
    response.put("model", "gpt-5-nano");
    response.put(
        "choices",
        List.of(
            Map.of(
                "index",
                0,
                "message",
                Map.of("role", "assistant", "content", contentJson),
                "finish_reason",
                "stop")));
    response.put("usage", Map.of("prompt_tokens", 10, "completion_tokens", 20, "total_tokens", 30));
    return response;
  }

  // ==================== Content 빌더 ====================

  /**
   * UserMessageAnalysisResult를 JSON 문자열로 변환합니다.
   *
   * <p>UserMessageAnalysisResult의 isXxx() 메서드들이 Jackson에 의해 필드로 직렬화되는 것을 방지하기 위해 필요한 필드만 Map으로
   * 구성합니다.
   */
  private static String buildContentJson(UserMessageAnalysisResult result) {
    Map<String, Object> contentMap = new HashMap<>();
    contentMap.put("intent", result.getIntent());
    contentMap.put("keywords", result.getKeywords() != null ? result.getKeywords() : List.of());

    if (result.getSemanticTags() != null) {
      contentMap.put("semantic_tags", result.getSemanticTags());
    }
    if (result.getReply() != null) {
      contentMap.put("reply", result.getReply());
    }

    return toJson(contentMap);
  }

  // ==================== 유틸리티 ====================

  private static String toJson(Object obj) {
    try {
      return OBJECT_MAPPER.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("JSON 직렬화 실패", e);
    }
  }
}
