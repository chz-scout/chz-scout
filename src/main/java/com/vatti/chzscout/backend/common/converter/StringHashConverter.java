package com.vatti.chzscout.backend.common.converter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * 조회용 단방향 해시 변환기.
 *
 * <p>이메일, 이름 등 민감 정보를 해시하여 검색 가능한 형태로 변환합니다. 동일한 입력에 대해 항상 동일한 해시값을 반환하므로 WHERE 절에서 검색이 가능합니다.
 */
@Component
public class StringHashConverter {

  private static final String ALGORITHM = "SHA-256";

  /**
   * 문자열을 SHA-256 해시로 변환합니다. (인스턴스 메서드)
   *
   * @param input 해시할 원본 문자열
   * @return Base64로 인코딩된 해시값, null 입력 시 null 반환
   */
  public String convertToHash(String input) {
    return hash(input);
  }

  /**
   * 문자열을 SHA-256 해시로 변환합니다. (정적 메서드)
   *
   * <p>Entity에서 직접 호출할 수 있도록 정적 메서드로 제공합니다.
   *
   * @param input 해시할 원본 문자열
   * @return Base64로 인코딩된 해시값, null 입력 시 null 반환
   */
  public static String hash(String input) {
    if (input == null) {
      return null;
    }

    try {
      MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
      // 소문자로 정규화하여 대소문자 구분 없이 동일한 해시 생성
      byte[] hashBytes = digest.digest(input.toLowerCase().getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hashBytes);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256은 모든 JVM에서 지원되므로 발생하지 않음
      throw new IllegalStateException("Hash algorithm not available: " + ALGORITHM, e);
    }
  }

  /**
   * 입력값과 해시값이 일치하는지 확인합니다.
   *
   * @param input 확인할 원본 문자열
   * @param hash 비교할 해시값
   * @return 일치하면 true
   */
  public boolean matches(String input, String hash) {
    if (input == null || hash == null) {
      return false;
    }
    return convertToHash(input).equals(hash);
  }
}
