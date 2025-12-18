package com.vatti.chzscout.backend.tag.fixture;

import com.vatti.chzscout.backend.tag.domain.entity.Tag;

/** Tag 엔티티 테스트 픽스처. */
public class TagFixture {

  private TagFixture() {}

  /** 기본 CUSTOM 태그 생성. */
  public static Tag createCustom() {
    return Tag.createCustom("테스트태그", 0L);
  }

  /** 이름 기반 CUSTOM 태그 생성. */
  public static Tag createCustom(String name) {
    return Tag.createCustom(name, 0L);
  }

  /** 이름과 사용 횟수 기반 CUSTOM 태그 생성. */
  public static Tag createCustom(String name, Long usageCount) {
    return Tag.createCustom(name, usageCount);
  }

  /** 인덱스 기반 CUSTOM 태그 생성. */
  public static Tag createCustomWithIndex(int index) {
    return Tag.createCustom("태그" + index, 0L);
  }

  /** 기본 CATEGORY 태그 생성. */
  public static Tag createCategory() {
    return Tag.createCategory("League of Legends", 0L);
  }

  /** 이름 기반 CATEGORY 태그 생성. */
  public static Tag createCategory(String name) {
    return Tag.createCategory(name, 0L);
  }

  /** 이름과 사용 횟수 기반 CATEGORY 태그 생성. */
  public static Tag createCategory(String name, Long usageCount) {
    return Tag.createCategory(name, usageCount);
  }

  /** 인덱스 기반 CATEGORY 태그 생성. */
  public static Tag createCategoryWithIndex(int index) {
    return Tag.createCategory("Category" + index, 0L);
  }
}
