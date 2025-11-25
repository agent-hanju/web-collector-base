package me.hanju.webcollectorbase.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

class JsonParserUtilTest {

  private static final ObjectMapper mapper = new ObjectMapper();

  private JsonNode parseJson(String json) throws Exception {
    return mapper.readTree(json);
  }

  @Nested
  @DisplayName("normalizeToArray 테스트")
  class NormalizeToArrayTest {

    @Test
    @DisplayName("null 입력시 빈 배열 반환")
    void nullInput() {
      ArrayNode result = JsonParserUtil.normalizeToArray(null);
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("JSON null 입력시 빈 배열 반환")
    void jsonNullInput() throws Exception {
      JsonNode nullNode = parseJson("null");
      ArrayNode result = JsonParserUtil.normalizeToArray(nullNode);
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("배열 입력시 그대로 반환")
    void arrayInput() throws Exception {
      JsonNode arrayNode = parseJson("[1, 2, 3]");
      ArrayNode result = JsonParserUtil.normalizeToArray(arrayNode);
      assertEquals(3, result.size());
    }

    @Test
    @DisplayName("단일 값 입력시 배열로 감싸서 반환")
    void singleValueInput() throws Exception {
      JsonNode valueNode = parseJson("\"test\"");
      ArrayNode result = JsonParserUtil.normalizeToArray(valueNode);
      assertEquals(1, result.size());
      assertEquals("test", result.get(0).asText());
    }

    @Test
    @DisplayName("객체 입력시 배열로 감싸서 반환")
    void objectInput() throws Exception {
      JsonNode objectNode = parseJson("{\"key\": \"value\"}");
      ArrayNode result = JsonParserUtil.normalizeToArray(objectNode);
      assertEquals(1, result.size());
      assertTrue(result.get(0).isObject());
    }
  }

  @Nested
  @DisplayName("getJsonObject 테스트")
  class GetJsonObjectTest {

    @Test
    @DisplayName("객체 값 추출")
    void extractObject() throws Exception {
      JsonNode json = parseJson("{\"data\": {\"id\": 1}}");
      JsonNode result = JsonParserUtil.getJsonObject(json, "data");
      assertEquals(1, result.get("id").asInt());
    }

    @Test
    @DisplayName("존재하지 않는 키는 null 반환")
    void missingKey() throws Exception {
      JsonNode json = parseJson("{\"data\": {\"id\": 1}}");
      assertNull(JsonParserUtil.getJsonObject(json, "missing"));
    }

    @Test
    @DisplayName("객체가 아닌 값은 null 반환")
    void nonObjectValue() throws Exception {
      JsonNode json = parseJson("{\"data\": \"string\"}");
      assertNull(JsonParserUtil.getJsonObject(json, "data"));
    }
  }

  @Nested
  @DisplayName("getString 테스트")
  class GetStringTest {

    @Test
    @DisplayName("문자열 값 추출")
    void extractString() throws Exception {
      JsonNode json = parseJson("{\"name\": \"test\"}");
      assertEquals("test", JsonParserUtil.getString(json, "name"));
    }

    @Test
    @DisplayName("숫자를 문자열로 변환")
    void numberToString() throws Exception {
      JsonNode json = parseJson("{\"count\": 42}");
      assertEquals("42", JsonParserUtil.getString(json, "count"));
    }

    @Test
    @DisplayName("존재하지 않는 키는 null 반환")
    void missingKey() throws Exception {
      JsonNode json = parseJson("{\"name\": \"test\"}");
      assertNull(JsonParserUtil.getString(json, "missing"));
    }

    @Test
    @DisplayName("null 값은 null 반환")
    void nullValue() throws Exception {
      JsonNode json = parseJson("{\"name\": null}");
      assertNull(JsonParserUtil.getString(json, "name"));
    }
  }

  @Nested
  @DisplayName("getInt 테스트")
  class GetIntTest {

    @Test
    @DisplayName("정수 값 추출 (기본값 버전)")
    void extractIntWithDefault() throws Exception {
      JsonNode json = parseJson("{\"count\": 42}");
      assertEquals(42, JsonParserUtil.getInt(json, "count", 0));
    }

    @Test
    @DisplayName("존재하지 않는 키는 기본값 반환")
    void missingKeyWithDefault() throws Exception {
      JsonNode json = parseJson("{\"count\": 42}");
      assertEquals(-1, JsonParserUtil.getInt(json, "missing", -1));
    }

    @Test
    @DisplayName("정수 값 추출 (nullable 버전)")
    void extractIntNullable() throws Exception {
      JsonNode json = parseJson("{\"count\": 42}");
      assertEquals(Integer.valueOf(42), JsonParserUtil.getInt(json, "count"));
    }

    @Test
    @DisplayName("문자열 정수 파싱")
    void parseStringInt() throws Exception {
      JsonNode json = parseJson("{\"count\": \"123\"}");
      assertEquals(Integer.valueOf(123), JsonParserUtil.getInt(json, "count"));
    }

    @Test
    @DisplayName("존재하지 않는 키는 null 반환")
    void missingKeyNullable() throws Exception {
      JsonNode json = parseJson("{\"count\": 42}");
      assertNull(JsonParserUtil.getInt(json, "missing"));
    }

    @Test
    @DisplayName("잘못된 형식은 null 반환")
    void invalidFormat() throws Exception {
      JsonNode json = parseJson("{\"count\": \"abc\"}");
      assertNull(JsonParserUtil.getInt(json, "count"));
    }
  }

  @Nested
  @DisplayName("getLong 테스트")
  class GetLongTest {

    @Test
    @DisplayName("long 값 추출 (기본값 버전)")
    void extractLongWithDefault() throws Exception {
      JsonNode json = parseJson("{\"id\": 9999999999}");
      assertEquals(9999999999L, JsonParserUtil.getLong(json, "id", 0L));
    }

    @Test
    @DisplayName("long 값 추출 (nullable 버전)")
    void extractLongNullable() throws Exception {
      JsonNode json = parseJson("{\"id\": 9999999999}");
      assertEquals(Long.valueOf(9999999999L), JsonParserUtil.getLong(json, "id"));
    }

    @Test
    @DisplayName("문자열 long 파싱")
    void parseStringLong() throws Exception {
      JsonNode json = parseJson("{\"id\": \"9999999999\"}");
      assertEquals(Long.valueOf(9999999999L), JsonParserUtil.getLong(json, "id"));
    }
  }

  @Nested
  @DisplayName("getDouble 테스트")
  class GetDoubleTest {

    @Test
    @DisplayName("double 값 추출 (기본값 버전)")
    void extractDoubleWithDefault() throws Exception {
      JsonNode json = parseJson("{\"rate\": 3.14}");
      assertEquals(3.14, JsonParserUtil.getDouble(json, "rate", 0.0), 0.001);
    }

    @Test
    @DisplayName("double 값 추출 (nullable 버전)")
    void extractDoubleNullable() throws Exception {
      JsonNode json = parseJson("{\"rate\": 3.14}");
      assertEquals(Double.valueOf(3.14), JsonParserUtil.getDouble(json, "rate"));
    }

    @Test
    @DisplayName("문자열 double 파싱")
    void parseStringDouble() throws Exception {
      JsonNode json = parseJson("{\"rate\": \"3.14\"}");
      assertEquals(3.14, JsonParserUtil.getDouble(json, "rate"), 0.001);
    }
  }

  @Nested
  @DisplayName("getBoolean 테스트")
  class GetBooleanTest {

    @Test
    @DisplayName("boolean 값 추출 (기본값 버전)")
    void extractBooleanWithDefault() throws Exception {
      JsonNode json = parseJson("{\"active\": true}");
      assertTrue(JsonParserUtil.getBoolean(json, "active", false));
    }

    @Test
    @DisplayName("존재하지 않는 키는 기본값 반환")
    void missingKeyWithDefault() throws Exception {
      JsonNode json = parseJson("{\"active\": true}");
      assertFalse(JsonParserUtil.getBoolean(json, "missing", false));
    }

    @Test
    @DisplayName("boolean 값 추출 (nullable 버전)")
    void extractBooleanNullable() throws Exception {
      JsonNode json = parseJson("{\"active\": true}");
      assertEquals(Boolean.TRUE, JsonParserUtil.getBoolean(json, "active"));
    }

    @Test
    @DisplayName("문자열 'true' 파싱")
    void parseStringTrue() throws Exception {
      JsonNode json = parseJson("{\"active\": \"true\"}");
      assertEquals(Boolean.TRUE, JsonParserUtil.getBoolean(json, "active"));
    }

    @Test
    @DisplayName("문자열 '1' 파싱")
    void parseStringOne() throws Exception {
      JsonNode json = parseJson("{\"active\": \"1\"}");
      assertEquals(Boolean.TRUE, JsonParserUtil.getBoolean(json, "active"));
    }

    @Test
    @DisplayName("문자열 'false' 파싱")
    void parseStringFalse() throws Exception {
      JsonNode json = parseJson("{\"active\": \"false\"}");
      assertEquals(Boolean.FALSE, JsonParserUtil.getBoolean(json, "active"));
    }

    @Test
    @DisplayName("문자열 '0' 파싱")
    void parseStringZero() throws Exception {
      JsonNode json = parseJson("{\"active\": \"0\"}");
      assertEquals(Boolean.FALSE, JsonParserUtil.getBoolean(json, "active"));
    }

    @Test
    @DisplayName("잘못된 문자열은 null 반환 (nullable 버전)")
    void invalidStringNullable() throws Exception {
      JsonNode json = parseJson("{\"active\": \"invalid\"}");
      assertNull(JsonParserUtil.getBoolean(json, "active"));
    }

    @Test
    @DisplayName("잘못된 문자열은 기본값 반환 (기본값 버전)")
    void invalidStringWithDefault() throws Exception {
      JsonNode json = parseJson("{\"active\": \"invalid\"}");
      assertTrue(JsonParserUtil.getBoolean(json, "active", true));
    }
  }
}
