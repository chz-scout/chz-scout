package com.vatti.chzscout.backend.auth.domain.dto;

/**
 * Discord 사용자 프로필 응답 DTO.
 *
 * @param id Discord 사용자 고유 ID
 * @param username 사용자명
 * @param globalName 표시 이름 (닉네임)
 * @param avatar 아바타 해시
 * @param email 이메일 (email scope)
 * @param verified 이메일 인증 여부
 */
public record DiscordUserProfile(
    String id, String username, String globalName, String avatar, String email, Boolean verified) {}
