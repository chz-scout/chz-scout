package com.vatti.chzscout.backend.tag.application.usecase;

import com.vatti.chzscout.backend.stream.domain.AllFieldLiveDto;
import com.vatti.chzscout.backend.tag.domain.dto.TagAutocompleteResponse;
import com.vatti.chzscout.backend.tag.domain.entity.TagType;
import java.util.List;

public interface TagUseCase {

  /**
   * 스트림 목록에서 태그를 추출하여 저장합니다.
   *
   * <p>삭제된 태그는 복구하고, 기존 태그는 사용 횟수를 증가시키고, 새로운 태그는 새로 생성합니다.
   *
   * @param streams 태그를 추출할 스트림 목록
   */
  void extractAndSaveTag(List<AllFieldLiveDto> streams);

  /**
   * prefix로 시작하는 태그를 검색합니다.
   *
   * @param prefix 검색할 접두어
   * @param tagType 태그 타입 (CATEGORY 또는 CUSTOM)
   * @param limit 최대 결과 수
   * @return 매칭되는 태그 목록 (인기순 정렬)
   */
  List<TagAutocompleteResponse> searchAutocomplete(String prefix, TagType tagType, int limit);

  /** DB에 저장된 태그를 Redis 자동완성 캐시에 갱신합니다. */
  void refreshAutocompleteCache();
}
