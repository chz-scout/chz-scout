package com.vatti.chzscout.backend.common.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StringHashConverterTest {

  private StringHashConverter converter = new StringHashConverter();
  String testEmail = "testEmail@email.com";

  @Nested
  @DisplayName("convertToHash 메서드 테스트")
  class ConvertToHashTest {

    @Test
    @DisplayName("문자열을 SHA-256 해시 + base64로 변환한다")
    void shouldConvertStringToHash() {
      // given

      // when
      String hashedEmail = converter.convertToHash(testEmail);

      // then
      assertThat(hashedEmail).isNotNull();
      assertThat(hashedEmail).isNotEqualTo(testEmail);
      assertThat(hashedEmail).isBase64();
    }

    @Test
    @DisplayName("동일한 입력에 대해 항상 동일한 해시 값 반환")
    void shouldReturnSameHashForSameInput() {
      // given

      // when
      String hashedEmail1 = converter.convertToHash(testEmail);
      String hashedEmail2 = converter.convertToHash(testEmail);

      // then
      assertThat(hashedEmail1).isEqualTo(hashedEmail2);
    }

    @Test
    @DisplayName("null 입력에 대해 null을 반환한다")
    void shouldReturnNullForNullInput() {
      // when
      String hash = converter.convertToHash(null);

      // then
      assertThat(hash).isNull();
    }

    @Test
    @DisplayName("다른 입력에 대해 다른 해시를 반환한다")
    void shouldReturnDifferentHashForDifferentInput() {
      // given
      String diffTestEmail = testEmail + "aaa";

      // when
      String hash1 = converter.convertToHash(testEmail);
      String hash2 = converter.convertToHash(diffTestEmail);

      // then
      assertThat(hash1).isNotEqualTo(hash2);
    }
  }

  @Nested
  @DisplayName("matches 메서드 테스트")
  class MatchesTest {

    @Test
    @DisplayName("입력값과 해시가 일치하면 true를 반환한다")
    void shouldReturnTrueWhenMatches() {
      // given
      String hash = converter.convertToHash(testEmail);

      // when
      boolean result = converter.matches(testEmail, hash);

      // then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("입력값과 해시가 일치하지 않으면 false를 반환한다")
    void shouldReturnFalseWhenNotMatches() {
      // given
      String wrongHash = converter.convertToHash(testEmail + "aaa");

      // when
      boolean result = converter.matches(testEmail, wrongHash);

      // then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("input이 null이면 false를 반환한다")
    void shouldReturnFalseWhenInputIsNull() {
      // given
      String hash = converter.convertToHash("test@example.com");

      // when
      boolean result = converter.matches(null, hash);

      // then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("hash가 null이면 false를 반환한다")
    void shouldReturnFalseWhenHashIsNull() {
      // when
      boolean result = converter.matches("test@example.com", null);

      // then
      assertThat(result).isFalse();
    }
  }
}
