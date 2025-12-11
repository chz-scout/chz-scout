package com.vatti.chzscout.backend.common.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StringEncryptConverterTest {

  StringEncryptor encryptor = createTestEncryptor();
  private final StringEncryptConverter converter = new StringEncryptConverter(encryptor);

  String testEmail = "testEmail@email.com";
  String name = "testName";
  String testKoreanName = "홍길동";

  /** 테스트용 Encryptor 생성 */
  private StringEncryptor createTestEncryptor() {
    PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
    SimpleStringPBEConfig config = new SimpleStringPBEConfig();

    config.setPassword("test-secret-key-for-unit-test");
    config.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
    config.setKeyObtentionIterations("100000");
    config.setPoolSize("1");
    config.setProviderName("SunJCE");
    config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
    config.setIvGeneratorClassName("org.jasypt.iv.RandomIvGenerator");
    config.setStringOutputType("base64");

    encryptor.setConfig(config);
    return encryptor;
  }

  @Nested
  @DisplayName("암호화/복호화 라운드트립")
  class RoundTripTest {

    @Test
    @DisplayName("암호화 후 다시 복호화하면 동일한 값이 나온다")
    void shouldRecoverOriginalAfterEncryptAndDecrypt() {
      // given

      // when
      String encryptedEmail = converter.convertToDatabaseColumn(testEmail);
      String decryptedEmail = converter.convertToEntityAttribute(encryptedEmail);

      // then
      assertThat(decryptedEmail).isEqualTo(testEmail);
    }

    @Test
    @DisplayName("암호화된 값은 원본과 다르다")
    void shouldProduceDifferentValueAfterEncryption() {
      // given

      // when
      String encryptedEmail = converter.convertToDatabaseColumn(testEmail);

      // then
      assertThat(encryptedEmail).isNotEqualTo(testEmail);
    }

    @Test
    @DisplayName("동일 값을 암호화해도 매번 다른 값이 나온다")
    void shouldProduceDifferentCiphertextForSameInput() {
      // given

      // when
      String encryptedEmail1 = converter.convertToDatabaseColumn(testEmail);
      String encryptedEmail2 = converter.convertToDatabaseColumn(testEmail);

      // then
      assertThat(encryptedEmail1).isNotEqualTo(encryptedEmail2);

      // 하지만 둘 다 복호화하면 원본 복구
      assertThat(converter.convertToEntityAttribute(encryptedEmail1)).isEqualTo(testEmail);
      assertThat(converter.convertToEntityAttribute(encryptedEmail2)).isEqualTo(testEmail);
    }

    @Test
    @DisplayName("한글 데이터도 정상적으로 암호화/복호화 가능")
    void shouldHandleKoreanText() {
      // given

      // when
      String encryptedKoreanName = converter.convertToDatabaseColumn(testKoreanName);
      String decryptedKoreanName = converter.convertToEntityAttribute(encryptedKoreanName);

      // then
      assertThat(decryptedKoreanName).isEqualTo(testKoreanName);
    }

    @Test
    @DisplayName("긴 문자열도 정상적으로 처리된다")
    void shouldHandleLongText() {
      // given
      String original = "a".repeat(1000);

      // when
      String encrypted = converter.convertToDatabaseColumn(original);
      String decrypted = converter.convertToEntityAttribute(encrypted);

      // then
      assertThat(decrypted).isEqualTo(original);
    }
  }

  @Nested
  @DisplayName("null 처리 테스트")
  class NullHandlingTest {

    @Test
    @DisplayName("null 입력 시 암호화 없이 다시 null 반환")
    void shouldReturnNullForNullInputOnEncrypt() {
      // when
      String result = converter.convertToDatabaseColumn(null);

      // then
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("null 입력 시 복호화 없이 다시 null 반환")
    void shouldReturnNullForNullInputOnDecrypt() {
      // when
      String result = converter.convertToEntityAttribute(null);

      // then
      assertThat(result).isNull();
    }
  }
}
