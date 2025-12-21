package com.vatti.chzscout.backend.tag.domain.dto;

public record TagAutocompleteResponse(String name, Long usageCount) {

  public static TagAutocompleteResponse of(String name, Long usageCount) {
    return new TagAutocompleteResponse(name, usageCount);
  }
}
