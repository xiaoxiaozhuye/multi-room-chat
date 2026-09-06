package com.multichat.infrastructure.mybatis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;

/** Maps PostgreSQL JSONB objects without exposing database-specific values to the domain. */
public class JsonMapTypeHandler extends BaseTypeHandler<Map<String, Object>> {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, Map<String, Object> value, JdbcType jdbcType)
            throws SQLException {
        try {
            statement.setObject(index, JSON.writeValueAsString(value), Types.OTHER);
        } catch (Exception exception) {
            throw new SQLException("Unable to serialize audit JSON", exception);
        }
    }

    @Override public Map<String, Object> getNullableResult(ResultSet resultSet, String column) throws SQLException { return read(resultSet.getString(column)); }
    @Override public Map<String, Object> getNullableResult(ResultSet resultSet, int index) throws SQLException { return read(resultSet.getString(index)); }
    @Override public Map<String, Object> getNullableResult(CallableStatement statement, int index) throws SQLException { return read(statement.getString(index)); }

    private Map<String, Object> read(String value) throws SQLException {
        if (value == null) return null;
        try { return JSON.readValue(value, MAP); } catch (Exception exception) { throw new SQLException("Unable to parse audit JSON", exception); }
    }
}
