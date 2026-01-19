package com.vatti.chzscout.backend.ai.infrastructure.type;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

/**
 * pgvector의 vector 타입을 float[]로 매핑하는 커스텀 Hibernate UserType.
 *
 * <p>PostgreSQL의 vector 타입과 Java의 float[] 사이 변환을 처리합니다.
 *
 * <p>사용법: 엔티티에 @TypeRegistration(basicClass = float[].class, userType = VectorType.class) 추가
 */
public class VectorType implements UserType<float[]> {

  @Override
  public int getSqlType() {
    return Types.OTHER;
  }

  @Override
  public Class<float[]> returnedClass() {
    return float[].class;
  }

  @Override
  public boolean equals(float[] x, float[] y) {
    return Arrays.equals(x, y);
  }

  @Override
  public int hashCode(float[] x) {
    return Arrays.hashCode(x);
  }

  @Override
  public float[] nullSafeGet(
      ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)
      throws SQLException {
    String value = rs.getString(position);
    if (value == null || rs.wasNull()) {
      return null;
    }
    return parseVector(value);
  }

  @Override
  public void nullSafeSet(
      PreparedStatement st, float[] value, int index, SharedSessionContractImplementor session)
      throws SQLException {
    if (value == null) {
      st.setNull(index, Types.OTHER);
    } else {
      st.setObject(index, formatVector(value), Types.OTHER);
    }
  }

  @Override
  public float[] deepCopy(float[] value) {
    if (value == null) {
      return null;
    }
    return Arrays.copyOf(value, value.length);
  }

  @Override
  public boolean isMutable() {
    return true;
  }

  @Override
  public Serializable disassemble(float[] value) {
    return deepCopy(value);
  }

  @Override
  public float[] assemble(Serializable cached, Object owner) {
    return deepCopy((float[]) cached);
  }

  /**
   * pgvector 문자열을 float[]로 파싱.
   *
   * @param vectorString "[1.0,2.0,3.0]" 형식
   * @return float 배열
   */
  private float[] parseVector(String vectorString) {
    String content = vectorString.substring(1, vectorString.length() - 1);
    if (content.isEmpty()) {
      return new float[0];
    }

    String[] parts = content.split(",");
    float[] result = new float[parts.length];
    for (int i = 0; i < parts.length; i++) {
      result[i] = Float.parseFloat(parts[i].trim());
    }
    return result;
  }

  /**
   * float[]를 pgvector 문자열로 변환.
   *
   * @param vector float 배열
   * @return "[1.0,2.0,3.0]" 형식
   */
  private String formatVector(float[] vector) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < vector.length; i++) {
      if (i > 0) {
        sb.append(",");
      }
      sb.append(vector[i]);
    }
    sb.append("]");
    return sb.toString();
  }
}
