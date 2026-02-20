package com.vatti.chzscout.backend.ai.application.usecase;

import com.vatti.chzscout.backend.member.domain.entity.Member;
import com.vatti.chzscout.backend.stream.domain.Stream;
import java.util.List;

/**
 * 벡터 임베딩 기반 방송 추천 UseCase.
 *
 * <p>사용자 쿼리를 임베딩 벡터로 변환하고, pgvector의 코사인 유사도 검색으로 방송을 추천합니다.
 */
public interface VectorRecommendUseCase {

  /**
   * 자연어 쿼리를 기반으로 방송을 추천합니다.
   *
   * @param query 사용자의 자연어 검색 쿼리 (예: "재미있는 FPS 게임 방송")
   * @param limit 반환할 최대 결과 수
   * @return 유사도가 높은 순으로 정렬된 방송 목록
   */
  List<Stream> recommend(String query, int limit);

  /**
   * 자연어 쿼리와 사용자 선호도를 기반으로 개인화된 방송을 추천합니다.
   *
   * @param query 사용자의 자연어 검색 쿼리 (예: "재미있는 FPS 게임 방송")
   * @param member 추천 대상 멤버 (선호 벡터 조회용)
   * @param limit 반환할 최대 결과 수
   * @return 유사도가 높은 순으로 정렬된 개인화 방송 목록
   */
  List<Stream> recommend(String query, Member member, int limit);
}
