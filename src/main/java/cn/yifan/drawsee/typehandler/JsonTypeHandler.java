package cn.yifan.drawsee.typehandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

/**
 * JSON类型处理器 用于处理数据库中的JSON字段与Java对象之间的转换
 *
 * @author devin
 * @date 2025-08-06
 */
@Slf4j
@MappedTypes({List.class, Object.class})
public class JsonTypeHandler extends BaseTypeHandler<Object> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType)
      throws SQLException {
    try {
      String jsonString = OBJECT_MAPPER.writeValueAsString(parameter);
      ps.setString(i, jsonString);
    } catch (JsonProcessingException e) {
      log.error("JSON序列化失败: {}", e.getMessage(), e);
      throw new SQLException("JSON序列化失败", e);
    }
  }

  @Override
  public Object getNullableResult(ResultSet rs, String columnName) throws SQLException {
    String jsonString = rs.getString(columnName);
    return parseJson(jsonString);
  }

  @Override
  public Object getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    String jsonString = rs.getString(columnIndex);
    return parseJson(jsonString);
  }

  @Override
  public Object getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    String jsonString = cs.getString(columnIndex);
    return parseJson(jsonString);
  }

  /**
   * 解析JSON字符串
   *
   * @param jsonString JSON字符串
   * @return 解析后的对象
   */
  private Object parseJson(String jsonString) {
    if (jsonString == null || jsonString.trim().isEmpty()) {
      return null;
    }

    try {
      // 尝试解析为List
      if (jsonString.startsWith("[")) {
        return OBJECT_MAPPER.readValue(jsonString, new TypeReference<List<Object>>() {});
      }
      // 尝试解析为Object
      else {
        return OBJECT_MAPPER.readValue(jsonString, Object.class);
      }
    } catch (JsonProcessingException e) {
      log.error("JSON反序列化失败: {}", e.getMessage(), e);
      // 如果解析失败，返回原始字符串
      return jsonString;
    }
  }
}
