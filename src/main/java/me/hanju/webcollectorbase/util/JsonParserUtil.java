package me.hanju.webcollectorbase.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/** Jackson 기반 JSON 파싱 유틸리티 클래스 */
@Deprecated(since = "0.2.0")
@Slf4j
@UtilityClass
public class JsonParserUtil {

  /**
   * JsonNode를 무조건 ArrayNode로 일반화한다. null의 경우 empty ArrayNode로 반환한다.
   *
   * @param element the JSON element to normalize
   * @return an ArrayNode (never null)
   */
  public ArrayNode normalizeToArray(JsonNode element) {
    ArrayNode array = JsonNodeFactory.instance.arrayNode();

    if (element == null || element.isNull()) {
      return array;
    }
    if (element.isArray()) {
      return (ArrayNode) element;
    }
    array.add(element);
    return array;
  }

  /**
   * value가 JSON 객체일 때 추출, 존재하지 않거나 객체가 아닐 시 null 반환
   *
   * @param json the JSON object
   * @param key  the key to extract
   * @return the JsonNode value, or null if not found or not an object
   */
  @Nullable
  public static JsonNode getJsonObject(JsonNode json, String key) {
    JsonNode elem = json.get(key);
    return (elem != null && elem.isObject()) ? elem : null;
  }

  /**
   * value가 값일 때 문자열로 추출, 존재하지 않거나 값이 아닐 시 null 반환
   *
   * @param json the JSON object
   * @param key  the key to extract
   * @return the string value, or null if not found or null
   */
  @Nullable
  public static String getString(JsonNode json, String key) {
    JsonNode elem = json.get(key);
    return (elem != null && !elem.isNull()) ? elem.asText() : null;
  }

  /**
   * value가 int로 변환가능한 값일 때 추출, 불가능할 경우 defaultValue 반환
   *
   * @param json         the JSON object
   * @param key          the key to extract
   * @param defaultValue the default value to return if extraction fails
   * @return the integer value, or defaultValue if not found or invalid
   */
  public static int getInt(JsonNode json, String key, int defaultValue) {
    JsonNode elem = json.get(key);
    if (elem != null && !elem.isNull()) {
      try {
        return elem.asInt(defaultValue);
      } catch (NumberFormatException e) {
        return defaultValue;
      }
    }
    return defaultValue;
  }

  /**
   * value가 Integer로 변환가능한 값일 때 추출, 불가능할 경우 null 반환
   *
   * @param json the JSON object
   * @param key  the key to extract
   * @return the Integer value, or null if not found or invalid
   */
  @Nullable
  public static Integer getInt(JsonNode json, String key) {
    JsonNode elem = json.get(key);
    if (elem == null || elem.isNull()) {
      return null;
    }

    try {
      // Try as number first
      if (elem.isNumber()) {
        return elem.asInt();
      }

      // Try as string (parse to Integer)
      String strValue = elem.asText();
      if (strValue != null && !strValue.trim().isEmpty()) {
        return Integer.parseInt(strValue);
      }
    } catch (NumberFormatException e) {
      // Invalid format, return null
    }

    return null;
  }

  /**
   * value가 long으로 변환가능한 값일 때 추출, 불가능할 경우 defaultValue 반환
   *
   * @param json         the JSON object
   * @param key          the key to extract
   * @param defaultValue the default value to return if extraction fails
   * @return the long value, or defaultValue if not found or invalid
   */
  public static long getLong(JsonNode json, String key, long defaultValue) {
    JsonNode elem = json.get(key);
    if (elem != null && !elem.isNull()) {
      try {
        return elem.asLong(defaultValue);
      } catch (NumberFormatException e) {
        return defaultValue;
      }
    }
    return defaultValue;
  }

  /**
   * value가 Long으로 변환가능한 값일 때 추출, 불가능할 경우 null 반환
   *
   * @param json the JSON object
   * @param key  the key to extract
   * @return the Long value, or null if not found or invalid
   */
  @Nullable
  public static Long getLong(JsonNode json, String key) {
    JsonNode elem = json.get(key);
    if (elem == null || elem.isNull()) {
      return null;
    }

    try {
      if (elem.isNumber()) {
        return elem.asLong();
      }

      String strValue = elem.asText();
      if (strValue != null && !strValue.trim().isEmpty()) {
        return Long.parseLong(strValue);
      }
    } catch (NumberFormatException e) {
      // Invalid format, return null
    }

    return null;
  }

  /**
   * value가 double로 변환가능한 값일 때 추출, 불가능할 경우 defaultValue 반환
   *
   * @param json         the JSON object
   * @param key          the key to extract
   * @param defaultValue the default value to return if extraction fails
   * @return the double value, or defaultValue if not found or invalid
   */
  public static double getDouble(JsonNode json, String key, double defaultValue) {
    JsonNode elem = json.get(key);
    if (elem != null && !elem.isNull()) {
      try {
        return elem.asDouble(defaultValue);
      } catch (NumberFormatException e) {
        return defaultValue;
      }
    }
    return defaultValue;
  }

  /**
   * value가 Double로 변환가능한 값일 때 추출, 불가능할 경우 null 반환
   *
   * @param json the JSON object
   * @param key  the key to extract
   * @return the Double value, or null if not found or invalid
   */
  @Nullable
  public static Double getDouble(JsonNode json, String key) {
    JsonNode elem = json.get(key);
    if (elem == null || elem.isNull()) {
      return null;
    }

    try {
      if (elem.isNumber()) {
        return elem.asDouble();
      }

      String strValue = elem.asText();
      if (strValue != null && !strValue.trim().isEmpty()) {
        return Double.parseDouble(strValue);
      }
    } catch (NumberFormatException e) {
      // Invalid format, return null
    }

    return null;
  }

  /**
   * value가 boolean으로 변환가능한 값일 때 추출, 불가능할 경우 defaultValue 반환
   *
   * @param json         the JSON object
   * @param key          the key to extract
   * @param defaultValue the default value to return if extraction fails
   * @return the boolean value, or defaultValue if not found or invalid
   */
  public static boolean getBoolean(JsonNode json, String key, boolean defaultValue) {
    JsonNode elem = json.get(key);
    if (elem == null || elem.isNull()) {
      return defaultValue;
    }

    if (elem.isBoolean()) {
      return elem.asBoolean();
    }

    String strValue = elem.asText();
    if (strValue != null) {
      String lower = strValue.trim().toLowerCase();
      if ("true".equals(lower) || "1".equals(lower)) {
        return true;
      }
      if ("false".equals(lower) || "0".equals(lower)) {
        return false;
      }
    }

    return defaultValue;
  }

  /**
   * value가 Boolean으로 변환가능한 값일 때 추출, 불가능할 경우 null 반환
   *
   * @param json the JSON object
   * @param key  the key to extract
   * @return the Boolean value, or null if not found or invalid
   */
  @Nullable
  public static Boolean getBoolean(JsonNode json, String key) {
    JsonNode elem = json.get(key);
    if (elem == null || elem.isNull()) {
      return null;
    }

    if (elem.isBoolean()) {
      return elem.asBoolean();
    }

    String strValue = elem.asText();
    if (strValue != null) {
      String lower = strValue.trim().toLowerCase();
      if ("true".equals(lower) || "1".equals(lower)) {
        return true;
      }
      if ("false".equals(lower) || "0".equals(lower)) {
        return false;
      }
    }

    return null;
  }
}
