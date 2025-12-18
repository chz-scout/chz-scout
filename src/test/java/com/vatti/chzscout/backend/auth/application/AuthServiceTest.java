package com.vatti.chzscout.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.vatti.chzscout.backend.auth.exception.AuthErrorCode;
import com.vatti.chzscout.backend.common.exception.BusinessException;
import com.vatti.chzscout.backend.member.domain.entity.Member;
import com.vatti.chzscout.backend.member.infrastructure.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @InjectMocks private AuthService authService;

  @Mock private MemberRepository memberRepository;
  @Mock private RefreshTokenService refreshTokenService;
  @Mock private JwtTokenProvider jwtTokenProvider;

  @Nested
  @DisplayName("findOrCreateMember 메서드")
  class FindOrCreateMemberTest {

    @Test
    @DisplayName("신규 회원 생성 - Discord ID가 존재하지 않는 경우")
    void createNewMember_WhenDiscordIdNotExists() {
      // given
      String discordId = "123456789";
      String nickname = "testUser";
      String email = "test@example.com";
      Member newMember = Member.create(discordId, nickname, email);

      given(memberRepository.findByDiscordId(discordId)).willReturn(Optional.empty());
      given(memberRepository.save(any(Member.class))).willReturn(newMember);

      // when
      Member result = authService.findOrCreateMember(discordId, nickname, email);

      // then
      assertThat(result.getDiscordId()).isEqualTo(discordId);
      assertThat(result.getNickname()).isEqualTo(nickname);
      then(memberRepository).should().save(any(Member.class));
    }

    @Test
    @DisplayName("기존 회원 조회 - Discord ID가 존재하는 경우")
    void findExistingMember_WhenDiscordIdExists() {
      // given
      String discordId = "123456789";
      String nickname = "testUser";
      String email = "test@example.com";
      Member existingMember = Member.create(discordId, nickname, email);

      given(memberRepository.findByDiscordId(discordId)).willReturn(Optional.of(existingMember));

      // when
      Member result = authService.findOrCreateMember(discordId, nickname, email);

      // then
      assertThat(result).isEqualTo(existingMember);
      then(memberRepository).should(never()).save(any(Member.class));
    }

    @Test
    @DisplayName("기존 회원 닉네임 업데이트 - 닉네임이 변경된 경우")
    void updateNickname_WhenNicknameChanged() {
      // given
      String discordId = "123456789";
      String oldNickname = "oldUser";
      String newNickname = "newUser";
      String email = "test@example.com";
      Member existingMember = Member.create(discordId, oldNickname, email);

      given(memberRepository.findByDiscordId(discordId)).willReturn(Optional.of(existingMember));

      // when
      Member result = authService.findOrCreateMember(discordId, newNickname, email);

      // then
      assertThat(result.getNickname()).isEqualTo(newNickname);
      then(memberRepository).should(never()).save(any(Member.class));
    }

    @Test
    @DisplayName("신규 회원 생성 - 이메일이 null인 경우에도 성공")
    void createNewMember_WithNullEmail() {
      // given
      String discordId = "123456789";
      String nickname = "testUser";
      Member newMember = Member.create(discordId, nickname, null);

      given(memberRepository.findByDiscordId(discordId)).willReturn(Optional.empty());
      given(memberRepository.save(any(Member.class))).willReturn(newMember);

      // when
      Member result = authService.findOrCreateMember(discordId, nickname, null);

      // then
      assertThat(result.getDiscordId()).isEqualTo(discordId);
      assertThat(result.getEmail()).isNull();
      then(memberRepository).should().save(any(Member.class));
    }
  }

  @Nested
  @DisplayName("logout 메서드")
  class LogoutTest {

    @Test
    @DisplayName("로그아웃 성공 - 유효한 토큰인 경우")
    void logout_Success_WithValidToken() {
      // given
      String refreshToken = "valid.refresh.token";
      String jti = "token-jti-12345";

      given(jwtTokenProvider.validateToken(refreshToken)).willReturn(true);
      given(jwtTokenProvider.getJti(refreshToken)).willReturn(jti);

      // when
      authService.logout(refreshToken);

      // then
      then(refreshTokenService).should().deleteByJti(jti);
    }

    @Test
    @DisplayName("로그아웃 실패 - 유효하지 않은 토큰인 경우")
    void logout_Fail_WithInvalidToken() {
      // given
      String refreshToken = "invalid.refresh.token";

      given(jwtTokenProvider.validateToken(refreshToken)).willReturn(false);

      // when & then
      assertThatThrownBy(() -> authService.logout(refreshToken))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              e ->
                  assertThat(((BusinessException) e).getErrorCode())
                      .isEqualTo(AuthErrorCode.INVALID_TOKEN));

      then(refreshTokenService).should(never()).deleteByJti(any());
    }
  }
}
