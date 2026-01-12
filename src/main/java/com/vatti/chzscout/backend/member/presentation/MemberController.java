package com.vatti.chzscout.backend.member.presentation;

import com.vatti.chzscout.backend.auth.domain.CustomUserDetails;
import com.vatti.chzscout.backend.common.response.ApiResponse;
import com.vatti.chzscout.backend.member.application.usecase.MemberUseCase;
import com.vatti.chzscout.backend.member.domain.dto.MemberResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 멤버 API 컨트롤러.
 *
 * <p>현재 로그인한 사용자 정보 조회 기능을 제공합니다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/members")
public class MemberController {

  private final MemberUseCase memberUseCase;

  /**
   * 현재 로그인한 사용자 정보를 조회합니다.
   *
   * <p>토큰이 유효하면 사용자 정보를 반환하고, 무효하면 401 Unauthorized가 반환됩니다.
   *
   * @param userDetails 인증된 사용자 정보 (JWT 토큰에서 추출)
   * @return 현재 로그인한 멤버 정보
   */
  @GetMapping("/me")
  public ApiResponse<MemberResponse> getMe(@AuthenticationPrincipal CustomUserDetails userDetails) {
    if (userDetails == null) {
      throw new IllegalStateException("인증 정보가 없습니다. 로그인이 필요합니다.");
    }
    return ApiResponse.success(memberUseCase.getCurrentMember(userDetails.getMember()));
  }

  /**
   * 알림 수신 설정을 변경합니다.
   *
   * @param enabled 알림 수신 여부 (true: 수신, false: 수신 안함)
   * @param userDetails 인증된 사용자 정보 (JWT 토큰에서 추출)
   * @return 변경된 알림 수신 여부
   */
  @PatchMapping("/me/notification")
  public ApiResponse<Boolean> updateNotification(
      @RequestParam boolean enabled, @AuthenticationPrincipal CustomUserDetails userDetails) {
    if (userDetails == null) {
      throw new IllegalStateException("인증 정보가 없습니다. 로그인이 필요합니다.");
    }
    return ApiResponse.success(
        memberUseCase.updateNotificationEnabled(userDetails.getMember(), enabled));
  }
}
