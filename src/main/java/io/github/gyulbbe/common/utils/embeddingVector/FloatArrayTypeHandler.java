package io.github.gyulbbe.common.utils.embeddingVector;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;

/**
 * Oracle VECTOR 타입과 float[] 배열을 변환하는 TypeHandler
 */
@MappedTypes(float[].class)
public class FloatArrayTypeHandler extends BaseTypeHandler<float[]> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, float[] parameter, JdbcType jdbcType) throws SQLException {
        // float[] 배열을 Oracle VECTOR 타입으로 변환
        Connection conn = ps.getConnection();

        // Oracle VECTOR 타입으로 변환하여 저장
        // VECTOR 타입은 문자열 형식으로 저장됨: '[0.1, 0.2, 0.3]'
        StringBuilder vectorStr = new StringBuilder("[");
        for (int j = 0; j < parameter.length; j++) {
            if (j > 0) {
                vectorStr.append(", ");
            }
            vectorStr.append(parameter[j]);
        }
        vectorStr.append("]");

        ps.setString(i, vectorStr.toString());
    }

    @Override
    public float[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseVector(rs.getString(columnName));
    }

    @Override
    public float[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseVector(rs.getString(columnIndex));
    }

    @Override
    public float[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseVector(cs.getString(columnIndex));
    }

    /**
     * Oracle VECTOR 문자열을 float[] 배열로 변환
     * 형식: "[0.1, 0.2, 0.3]"
     */
    private float[] parseVector(String vectorStr) {
        if (vectorStr == null || vectorStr.isEmpty()) {
            return null;
        }

        // 대괄호 제거
        vectorStr = vectorStr.trim();
        if (vectorStr.startsWith("[")) {
            vectorStr = vectorStr.substring(1);
        }
        if (vectorStr.endsWith("]")) {
            vectorStr = vectorStr.substring(0, vectorStr.length() - 1);
        }

        // 콤마로 분리하여 float 배열로 변환
        String[] parts = vectorStr.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }

        return result;
    }
}