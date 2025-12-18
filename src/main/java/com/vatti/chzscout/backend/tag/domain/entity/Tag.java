package com.vatti.chzscout.backend.tag.domain.entity;

import com.vatti.chzscout.backend.common.domain.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "tag", uniqueConstraints = @UniqueConstraint(columnNames = {"name", "tag_type"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tag extends BaseEntity {

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TagType tagType;

  @Column(nullable = false)
  private Long usageCount = 0L;

  private Tag(String name, TagType tagType) {
    this.name = name;
    this.tagType = tagType;
  }

  private Tag(String name, TagType tagType, Long usageCount) {
    this.name = name;
    this.tagType = tagType;
    this.usageCount = usageCount;
  }

  public static Tag createCategory(String name) {
    return new Tag(name, TagType.CATEGORY);
  }

  public static Tag createCategory(String name, Long usageCount) {
    return new Tag(name, TagType.CATEGORY, usageCount);
  }

  public static Tag createCustom(String name) {
    return new Tag(name, TagType.CUSTOM);
  }

  public static Tag createCustom(String name, Long usageCount) {
    return new Tag(name, TagType.CUSTOM, usageCount);
  }

  public void increaseUsageCount() {
    this.usageCount++;
  }

  public void increaseUsageCount(Long usageCount) {
    this.usageCount += usageCount;
  }
}
