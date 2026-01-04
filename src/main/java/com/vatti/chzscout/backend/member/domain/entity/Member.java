package com.vatti.chzscout.backend.member.domain.entity;

import com.vatti.chzscout.backend.common.converter.StringEncryptConverter;
import com.vatti.chzscout.backend.common.converter.StringHashConverter;
import com.vatti.chzscout.backend.common.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

  /** 외부 노출용 고유 식별자. API 응답, JWT에 사용. */
  @Column(name = "uuid", nullable = false, unique = true, updatable = false)
  private String uuid;

  @Column(name = "discord_id", nullable = false, unique = true)
  private String discordId;

  @Column(name = "nickname", nullable = false)
  private String nickname;

  /** 암호화되어 저장됨. 복호화된 평문으로 조회됨. */
  @Convert(converter = StringEncryptConverter.class)
  @Column(name = "email", unique = true)
  private String email;

  /** 이메일 검색용 해시. WHERE email_hash = ? 로 조회 가능. */
  @Column(name = "email_hash", unique = true)
  private String emailHash;

  @Column(name = "role")
  private String role;

  /** DM 알림 수신 여부. true면 태그 매칭 시 Discord DM으로 알림 발송. */
  @Column(name = "notification_enabled", nullable = false)
  private boolean notificationEnabled = false;

  private Member(String discordId, String nickname, String email) {
    this.uuid = UUID.randomUUID().toString();
    this.discordId = discordId;
    this.nickname = nickname;
    this.role = "USER";
    if (email != null) {
      updateEmail(email);
    }
  }

  /**
   * Discord OAuth 로그인 시 Member 생성.
   *
   * @param discordId Discord 사용자 ID
   * @param nickname Discord 사용자명
   * @param email Discord 이메일 (nullable)
   * @return 새로운 Member 인스턴스 (uuid 자동 생성)
   */
  public static Member create(String discordId, String nickname, String email) {
    return new Member(discordId, nickname, email);
  }

  /**
   * Discord 사용자명 업데이트.
   *
   * @param nickname 새로운 Discord 사용자명
   */
  public void updateNickname(String nickname) {
    this.nickname = nickname;
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
   * 프로필 전체 업데이트.
   *
   * @param email 이메일 (평문)
   * @param nickname 닉네임
   */
  public void updateProfile(String email, String nickname) {
    updateEmail(email);
    updateNickname(nickname);
  }

  /**
   * DM 알림 수신 설정을 변경합니다.
   *
   * @param enabled true면 알림 수신, false면 알림 미수신
   */
  public void updateNotificationEnabled(boolean enabled) {
    this.notificationEnabled = enabled;
  }
}
