package com.vatti.chzscout.backend.member.fixture;

import com.vatti.chzscout.backend.member.domain.entity.Member;

/** Member 엔티티 테스트 픽스처. */
public class MemberFixture {

  private MemberFixture() {}

  /** 기본 Member 생성. */
  public static Member create() {
    return Member.create("123456789", "테스트유저", "test@test.com");
  }

  /** Discord ID 기반 Member 생성. */
  public static Member create(String discordId) {
    return Member.create(discordId, "유저_" + discordId, discordId + "@test.com");
  }

  /** Discord ID와 닉네임 기반 Member 생성. */
  public static Member create(String discordId, String nickname) {
    return Member.create(discordId, nickname, discordId + "@test.com");
  }

  /** 인덱스 기반 Member 생성. */
  public static Member createWithIndex(int index) {
    return Member.create("discord_" + index, "유저" + index, "user" + index + "@test.com");
  }

  /** 알림 허용된 Member 생성. */
  public static Member createWithNotificationEnabled(String discordId) {
    Member member = Member.create(discordId, "유저_" + discordId, discordId + "@test.com");
    member.updateNotificationEnabled(true);
    return member;
  }

  /** 알림 비허용 Member 생성 (기본값). */
  public static Member createWithNotificationDisabled(String discordId) {
    return Member.create(discordId, "유저_" + discordId, discordId + "@test.com");
  }
}
