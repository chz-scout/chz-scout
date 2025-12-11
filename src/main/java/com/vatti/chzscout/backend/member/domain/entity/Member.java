package com.vatti.chzscout.backend.member.domain.entity;

import com.vatti.chzscout.backend.common.converter.StringEncryptConverter;
import com.vatti.chzscout.backend.common.converter.StringHashConverter;
import com.vatti.chzscout.backend.common.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

  @Column(name = "discord_id", nullable = false, unique = true)
  private String discordId;

  @Column(name = "discord_username", nullable = false)
  private String discordUsername;

  /** 암호화되어 저장됨. 복호화된 평문으로 조회됨. */
  @Convert(converter = StringEncryptConverter.class)
  @Column(name = "email", unique = true)
  private String email;

  /** 이메일 검색용 해시. WHERE email_hash = ? 로 조회 가능. */
  @Column(name = "email_hash", unique = true)
  private String emailHash;

  /** 암호화되어 저장됨. 복호화된 평문으로 조회됨. */
  @Convert(converter = StringEncryptConverter.class)
  @Column(name = "name")
  private String name;

  /** 이름 검색용 해시. */
  @Column(name = "name_hash")
  private String nameHash;

  private Member(String discordId, String discordUsername) {
    this.discordId = discordId;
    this.discordUsername = discordUsername;
  }

  /**
   * Discord OAuth 로그인 시 Member 생성.
   *
   * @param discordId Discord 사용자 ID
   * @param discordUsername Discord 사용자명
   * @return 새로운 Member 인스턴스
   */
  public static Member create(String discordId, String discordUsername) {
    return new Member(discordId, discordUsername);
  }

  /**
   * Discord 사용자명 업데이트.
   *
   * @param discordUsername 새로운 Discord 사용자명
   */
  public void updateDiscordUsername(String discordUsername) {
    this.discordUsername = discordUsername;
  }

  /**
   * 이메일 업데이트. 암호화 필드와 해시 필드가 함께 설정됩니다.
   *
   * @param email 새로운 이메일 (평문)
   */
  public void updateEmail(String email) {
    this.email = email;
    this.emailHash = StringHashConverter.hash(email);
  }

  /**
   * 이름 업데이트. 암호화 필드와 해시 필드가 함께 설정됩니다.
   *
   * @param name 새로운 이름 (평문)
   */
  public void updateName(String name) {
    this.name = name;
    this.nameHash = StringHashConverter.hash(name);
  }

  /**
   * 프로필 전체 업데이트.
   *
   * @param email 이메일 (평문)
   * @param name 이름 (평문)
   */
  public void updateProfile(String email, String name) {
    updateEmail(email);
    updateName(name);
  }
}
