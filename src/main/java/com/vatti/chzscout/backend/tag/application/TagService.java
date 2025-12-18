package com.vatti.chzscout.backend.tag.application;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import com.vatti.chzscout.backend.stream.domain.AllFieldLiveDto;
import com.vatti.chzscout.backend.tag.domain.entity.Tag;
import com.vatti.chzscout.backend.tag.domain.entity.TagType;
import com.vatti.chzscout.backend.tag.infrastructure.TagRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class TagService {
  private final TagRepository tagRepository;

  /**
   * 스트림 목록에서 태그를 추출하여 저장합니다.
   *
   * <p>삭제된 태그는 복구하고, 기존 태그는 사용 횟수를 증가시키고, 새로운 태그는 새로 생성합니다.
   *
   * @param streams 태그를 추출할 스트림 목록
   */
  public void extractAndSaveTag(List<AllFieldLiveDto> streams) {
    // 1. 스트림 목록에서 태그 및 카테고리 별 등장 횟수 집계
    Map<String, Long> tagCountMap =
        streams.stream()
            .flatMap(stream -> stream.tags().stream())
            .filter(tag -> tag != null && !tag.isBlank())
            .collect(Collectors.groupingBy(identity(), Collectors.counting()));

    Map<String, Long> categoryCountMap =
        streams.stream()
            .map(AllFieldLiveDto::liveCategoryValue)
            .filter(category -> category != null && !category.isBlank())
            .collect(Collectors.groupingBy(identity(), Collectors.counting()));

    // 디버깅: 첫 번째 스트림의 실제 데이터 확인
    if (!streams.isEmpty()) {
      AllFieldLiveDto sample = streams.get(0);
      log.info(
          "Sample stream - title: {}, tags: {}, liveCategory: {}, categoryType: {}",
          sample.liveTitle(),
          sample.tags(),
          sample.liveCategory(),
          sample.categoryType());
    }

    log.info("Collected tags: {} (count: {})", tagCountMap.keySet(), tagCountMap.size());
    log.info(
        "Collected categories: {} (count: {})", categoryCountMap.keySet(), categoryCountMap.size());

    if (tagCountMap.isEmpty() && categoryCountMap.isEmpty()) {
      return;
    }

    // 2. DB에서 태그 조회 (삭제된 태그 포함) - 빈 Set은 쿼리 안 함
    Map<String, Tag> allTags =
        tagCountMap.isEmpty()
            ? Map.of()
            : tagRepository.findCustomByNameInIncludingDeleted(tagCountMap.keySet()).stream()
                .collect(toMap(Tag::getName, identity()));

    Map<String, Tag> allCategories =
        categoryCountMap.isEmpty()
            ? Map.of()
            : tagRepository.findCategoryByNameInIncludingDeleted(categoryCountMap.keySet()).stream()
                .collect(toMap(Tag::getName, identity()));

    // 3. 태그 복구, 업데이트, 생성 처리
    processTagsAndSave(tagCountMap, allTags, TagType.CUSTOM);
    processTagsAndSave(categoryCountMap, allCategories, TagType.CATEGORY);
  }

  /**
   * 태그 복구 및 생성 로직
   *
   * @param tagCountMap 태그 이름 → 등장 횟수
   * @param allTags DB에 존재하는 태그 (삭제된 것 포함)
   * @param tagType 처리할 태그 타입
   */
  private void processTagsAndSave(
      Map<String, Long> tagCountMap, Map<String, Tag> allTags, TagType tagType) {
    if (tagCountMap.isEmpty()) {
      return;
    }

    // 삭제된 태그 복구 (Native Query로 deleted = false 처리)
    Set<String> allTagNames = allTags.keySet();
    restoreByTagType(allTagNames, tagType);

    List<Tag> newTags = new ArrayList<>();
    for (var entry : tagCountMap.entrySet()) {
      String name = entry.getKey();
      Long tagCount = entry.getValue();

      if (allTagNames.contains(name)) {
        // 기존 태그 (활성 또는 복구됨): usageCount 증가
        Tag tag = allTags.get(name);
        tag.increaseUsageCount(tagCount);
      } else {
        // 신규 태그: 새로 생성
        newTags.add(createTagByType(name, tagCount, tagType));
      }
    }

    log.info(
        "Saving {} new tags of type {}: {}",
        newTags.size(),
        tagType,
        newTags.stream().map(Tag::getName).toList());
    tagRepository.saveAll(newTags);
  }

  private void restoreByTagType(Set<String> names, TagType tagType) {
    if (names.isEmpty()) {
      return;
    }
    switch (tagType) {
      case CUSTOM -> tagRepository.restoreCustomByNames(names);
      case CATEGORY -> tagRepository.restoreCategoryByNames(names);
    }
  }

  private Tag createTagByType(String name, Long usageCount, TagType tagType) {
    return switch (tagType) {
      case CUSTOM -> Tag.createCustom(name, usageCount);
      case CATEGORY -> Tag.createCategory(name, usageCount);
    };
  }
}
