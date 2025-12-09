package com.vatti.chzscout.backend.example.domain.entity;

import com.vatti.chzscout.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "examples")
public class Example extends BaseEntity {

  @Column(nullable = false, length = 50)
  private String name;

  @Column(nullable = false, unique = true, length = 100)
  private String email;

  private Example(String name, String email) {
    this.name = name;
    this.email = email;
  }

  public static Example create(String name, String email) {
    return new Example(name, email);
  }

  public void updateName(String name) {
    this.name = name;
  }
}
