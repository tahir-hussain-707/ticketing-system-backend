package com.frequency.ticketing.domain.knowledgebase;

import com.pgvector.PGvector;
import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

/**
 * Maps {@code float[]} to PostgreSQL's {@code vector} column type via pgvector-java's
 * {@link PGvector} (research.md "Vector storage"). The {@code pgvector} extension itself
 * (V6 migration) must be enabled on the target database for this type to bind correctly.
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
    return Objects.deepEquals(x, y);
  }

  @Override
  public int hashCode(float[] x) {
    return Objects.hashCode(x);
  }

  @Override
  public float[] nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)
      throws SQLException {
    Object value = rs.getObject(position);
    if (value == null) {
      return null;
    }
    return new PGvector(value.toString()).toArray();
  }

  @Override
  public void nullSafeSet(
      PreparedStatement st, float[] value, int index, SharedSessionContractImplementor session)
      throws SQLException {
    st.setObject(index, value == null ? null : new PGvector(value));
  }

  @Override
  public float[] deepCopy(float[] value) {
    return value == null ? null : value.clone();
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

  @Override
  public float[] replace(float[] detached, float[] managed, Object owner) {
    return deepCopy(detached);
  }
}
