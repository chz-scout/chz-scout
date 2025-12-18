package com.vatti.chzscout.backend.auth.application;

import com.vatti.chzscout.backend.auth.exception.AuthErrorCode;
import com.vatti.chzscout.backend.common.exception.BusinessException;
import com.vatti.chzscout.backend.member.domain.entity.Member;
import com.vatti.chzscout.backend.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인증 관련 비즈니스 로직을 처리하는 서비스. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

  private final MemberRepository memberRepository;
  private final RefreshTokenService refreshTokenService;
  private final JwtTokenProvider jwtTokenProvider;

  /**
   * Discord ID로 회원 조회 또는 생성. 사용자명이 변경된 경우 업데이트.
   *
   * @param discordId Discord 사용자 ID
   * @param nickname Discord 사용자명
   * @param email Discord 이메일 (nullable)
   * @return 기존 회원 (업데이트 포함) 또는 새로 생성된 회원
   */
  @Transactional
  public Member findOrCreateMember(String discordId, String nickname, String email) {
    return memberRepository
        .findByDiscordId(discordId)
        .map(
            member -> {
              // 사용자명 변경 시 업데이트 (Dirty Checking으로 자동 반영)
              if (nickname != null && !member.getNickname().equals(nickname)) {
                log.info("Discord 사용자명 변경: {} -> {}", member.getNickname(), nickname);
                member.updateNickname(nickname);
              }
              log.debug("기존 회원 조회: discordId={}", discordId);
              return member;
            })
        .orElseGet(
            () -> {
              log.info("신규 회원 생성: discordId={}, username={}", discordId, nickname);
              return memberRepository.save(Member.create(discordId, nickname, email));
            });
  }

  /**
   * 로그아웃 처리. Refresh Token을 Redis에서 삭제.
   *
   * @param refreshToken 삭제할 Refresh Token
   * @throws BusinessException 토큰이 유효하지 않은 경우
   */
  public void logout(String refreshToken) {
    if (!jwtTokenProvider.validateToken(refreshToken)) {
      log.warn("로그아웃 실패: 유효하지 않은 토큰");
      throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
    }

    String jti = jwtTokenProvider.getJti(refreshToken);
    refreshTokenService.deleteByJti(jti);
    log.debug("Refresh Token 삭제 완료: jti={}", jti);
  }
}
