package com.vatti.chzscout.backend.member.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MemberTest {
  private final String discordId = "123456789";
  private final String nickname = "testUser";
  private final String email = "test@example.com";

  @Nested
  @DisplayName("create 메서드")
  class CreateTest {

    @Test
    @DisplayName("Member 생성 성공 - 모든 필드 제공")
    void create_Success_WithAllFields() {
      // given

      // when
      Member member = Member.create(discordId, nickname, email);

      // then
      assertThat(member.getDiscordId()).isEqualTo(discordId);
      assertThat(member.getNickname()).isEqualTo(nickname);
      assertThat(member.getEmail()).isEqualTo(email);
      assertThat(member.getEmailHash()).isNotNull();
      assertThat(member.getUuid()).isNotNull();
      assertThat(member.getRole()).isEqualTo("USER");
    }

    @Test
    @DisplayName("Member 생성 성공 - 이메일 없이 생성")
    void create_Success_WithoutEmail() {
      // given

      // when
      Member member = Member.create(discordId, nickname, null);

      // then
      assertThat(member.getDiscordId()).isEqualTo(discordId);
      assertThat(member.getNickname()).isEqualTo(nickname);
      assertThat(member.getEmail()).isNull();
      assertThat(member.getEmailHash()).isNull();
      assertThat(member.getUuid()).isNotNull();
      assertThat(member.getRole()).isEqualTo("USER");
    }

    @Test
    @DisplayName("Member 생성 시 UUID 자동 생성")
    void create_GeneratesUuid() {
      // given

      // when
      Member member1 = Member.create(discordId, nickname, null);
      Member member2 = Member.create(discordId, nickname, null);

      // then
      assertThat(member1.getUuid()).isNotNull();
      assertThat(member2.getUuid()).isNotNull();
      assertThat(member1.getUuid()).isNotEqualTo(member2.getUuid());
    }
  }

  @Nested
  @DisplayName("updateNickname 메서드")
  class UpdateNicknameTest {

    @Test
    @DisplayName("닉네임 업데이트 성공")
    void updateNickname_Success() {
      // given
      Member member = Member.create(discordId, nickname, null);

      // when
      member.updateNickname(email);

      // then
      assertThat(member.getNickname()).isEqualTo(email);
    }
  }

  @Nested
  @DisplayName("updateEmail 메서드")
  class UpdateEmailTest {

    @Test
    @DisplayName("이메일 업데이트 성공 - 해시도 함께 업데이트")
    void updateEmail_Success_WithHash() {
      // given
      Member member = Member.create(discordId, nickname, null);

      // when
      member.updateEmail(email);

      // then
      assertThat(member.getEmail()).isEqualTo(email);
      assertThat(member.getEmailHash()).isNotNull();
    }

    @Test
    @DisplayName("이메일 업데이트 - 같은 이메일은 같은 해시 생성")
    void updateEmail_SameEmailProducesSameHash() {
      // given
      Member member1 = Member.create(discordId, nickname, null);
      Member member2 = Member.create(discordId, nickname + "2", null);
      String email = "same@example.com";

      // when
      member1.updateEmail(email);
      member2.updateEmail(email);

      // then
      assertThat(member1.getEmailHash()).isEqualTo(member2.getEmailHash());
    }
  }

  @Nested
  @DisplayName("updateProfile 메서드")
  class UpdateProfileTest {

    @Test
    @DisplayName("프로필 전체 업데이트 성공")
    void updateProfile_Success() {
      // given
      Member member = Member.create(discordId, nickname, email);
      String newEmail = "new@example.com";
      String newNickname = "newNickname";

      // when
      member.updateProfile(newEmail, newNickname);

      // then
      assertThat(member.getEmail()).isEqualTo(newEmail);
      assertThat(member.getNickname()).isEqualTo(newNickname);
      assertThat(member.getEmailHash()).isNotNull();
    }
  }

  @Nested
  @DisplayName("notificationEnabled 필드")
  class NotificationEnabledTest {

    @Test
    @DisplayName("생성 시 기본값은 false이다")
    void create_DefaultNotificationEnabledIsFalse() {
      // given & when
      Member member = Member.create(discordId, nickname, email);

      // then
      assertThat(member.isNotificationEnabled()).isFalse();
    }

    @Test
    @DisplayName("알림 설정을 true로 변경할 수 있다")
    void updateNotificationEnabled_ToTrue() {
      // given
      Member member = Member.create(discordId, nickname, email);

      // when
      member.updateNotificationEnabled(true);

      // then
      assertThat(member.isNotificationEnabled()).isTrue();
    }

    @Test
    @DisplayName("알림 설정을 false로 변경할 수 있다")
    void updateNotificationEnabled_ToFalse() {
      // given
      Member member = Member.create(discordId, nickname, email);
      member.updateNotificationEnabled(true);

      // when
      member.updateNotificationEnabled(false);

      // then
      assertThat(member.isNotificationEnabled()).isFalse();
    }
  }
}
