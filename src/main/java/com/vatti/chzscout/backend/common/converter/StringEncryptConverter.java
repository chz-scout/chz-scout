package com.vatti.chzscout.backend.common.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * DB 필드 암호화/복호화를 위한 JPA Converter.
 *
 * <p>Entity 필드에 {@code @Convert(converter = StringEncryptConverter.class)}를 적용하면 DB 저장 시 자동으로
 * 암호화되고, 조회 시 자동으로 복호화됩니다.
 */
@Converter
@Component
public class StringEncryptConverter implements AttributeConverter<String, String> {

  private final StringEncryptor encryptor;

  public StringEncryptConverter(@Qualifier("jasyptEncryptorAES") StringEncryptor encryptor) {
    this.encryptor = encryptor;
  }

  /**
   * Entity → DB 저장 시 호출 (암호화)
   *
   * @param attribute Entity의 평문 값
   * @return DB에 저장될 암호화된 값
   */
  @Override
  public String convertToDatabaseColumn(String attribute) {
    if (attribute == null) {
      return null;
    }
    return encryptor.encrypt(attribute);
  }

  /**
   * DB → Entity 조회 시 호출 (복호화)
   *
   * @param dbData DB에서 읽어온 암호화된 값
   * @return Entity에 매핑될 복호화된 평문 값
   */
  @Override
  public String convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    return encryptor.decrypt(dbData);
  }
}
