package com.vatti.chzscout.backend.stream.application.usecase;

import com.vatti.chzscout.backend.stream.domain.Stream;
import java.util.List;

/**
 * 방송 추천 UseCase.
 *
 * <p>의미 태그를 기반으로 Redis에 캐싱된 방송 중 매칭되는 방송을 추천합니다.
 */
public interface RecommendStreamUseCase {

  /**
   * 의미 태그 목록을 기반으로 방송을 추천합니다.
   *
   * @param semanticTags AI가 추출한 의미 태그 목록
   * @return 매칭된 방송 목록 (관련도 순 정렬)
   */
  List<Stream> recommend(List<String> semanticTags);
}
