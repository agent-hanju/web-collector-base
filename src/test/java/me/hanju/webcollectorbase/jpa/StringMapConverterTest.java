package me.hanju.webcollectorbase.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StringMapConverterTest {

  private StringMapConverter converter;

  @BeforeEach
  void setUp() {
    converter = new StringMapConverter();
  }

  @Test
  @DisplayName("Map을 JSON 문자열로 변환")
  void convertToDatabaseColumn() {
    Map<String, String> map = new HashMap<>();
    map.put("key1", "value1");
    map.put("key2", "value2");

    String json = converter.convertToDatabaseColumn(map);

    assertTrue(json.contains("\"key1\":\"value1\""));
    assertTrue(json.contains("\"key2\":\"value2\""));
  }

  @Test
  @DisplayName("null Map은 null 반환")
  void convertNullMapToNull() {
    assertNull(converter.convertToDatabaseColumn(null));
  }

  @Test
  @DisplayName("빈 Map은 null 반환")
  void convertEmptyMapToNull() {
    assertNull(converter.convertToDatabaseColumn(new HashMap<>()));
  }

  @Test
  @DisplayName("JSON 문자열을 Map으로 변환")
  void convertToEntityAttribute() {
    String json = "{\"key1\":\"value1\",\"key2\":\"value2\"}";

    Map<String, String> map = converter.convertToEntityAttribute(json);

    assertEquals("value1", map.get("key1"));
    assertEquals("value2", map.get("key2"));
  }

  @Test
  @DisplayName("null JSON은 null Map 반환")
  void convertNullJsonToNullMap() {
    assertNull(converter.convertToEntityAttribute(null));
  }

  @Test
  @DisplayName("빈 JSON은 null Map 반환")
  void convertEmptyJsonToNullMap() {
    assertNull(converter.convertToEntityAttribute(""));
    assertNull(converter.convertToEntityAttribute("   "));
  }

  @Test
  @DisplayName("양방향 변환 테스트")
  void roundTrip() {
    Map<String, String> original = new HashMap<>();
    original.put("field", "value");

    String json = converter.convertToDatabaseColumn(original);
    Map<String, String> restored = converter.convertToEntityAttribute(json);

    assertEquals(original, restored);
  }
}
