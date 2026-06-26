package com.oaschool.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.postgresql.util.PGobject;

public final class Rows {
  private static final ObjectMapper mapper = new ObjectMapper();

  private Rows() {}

  public static String str(Map<String, Object> row, String key) {
    Object value = row.get(key);
    return value == null ? null : String.valueOf(value);
  }

  public static UUID uuid(Map<String, Object> row, String key) {
    Object value = row.get(key);
    if (value instanceof UUID uuid) return uuid;
    return value == null ? null : UUID.fromString(String.valueOf(value));
  }

  public static double dbl(Map<String, Object> row, String key) {
    Object value = row.get(key);
    if (value instanceof BigDecimal decimal) return decimal.doubleValue();
    if (value instanceof Number number) return number.doubleValue();
    return value == null ? 0 : Double.parseDouble(String.valueOf(value));
  }

  public static int integer(Map<String, Object> row, String key) {
    Object value = row.get(key);
    if (value instanceof Number number) return number.intValue();
    return value == null ? 0 : Integer.parseInt(String.valueOf(value));
  }

  public static boolean bool(Map<String, Object> row, String key) {
    Object value = row.get(key);
    if (value instanceof Boolean bool) return bool;
    return value != null && Boolean.parseBoolean(String.valueOf(value));
  }

  public static Object json(Object value) {
    if (value instanceof PGobject pg) {
      try {
        return mapper.readValue(pg.getValue(), new TypeReference<List<Object>>() {});
      } catch (JsonProcessingException ignored) {
        return new ArrayList<>();
      }
    }
    return value;
  }

  public static Object time(Object value) {
    if (value instanceof Timestamp timestamp) return timestamp.toInstant().toString();
    if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toString();
    return value;
  }

  public static Map<String, Object> normalize(Map<String, Object> row) {
    Map<String, Object> mapped = new LinkedHashMap<>();
    row.forEach((key, value) -> {
      Object next = value;
      if (value instanceof PGobject) next = json(value);
      if (value instanceof Timestamp || value instanceof OffsetDateTime) next = time(value);
      if (value instanceof BigDecimal decimal) next = decimal.doubleValue();
      mapped.put(key, next);
    });
    return mapped;
  }
}
