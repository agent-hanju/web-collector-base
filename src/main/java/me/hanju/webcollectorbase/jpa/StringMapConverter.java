package me.hanju.webcollectorbase.jpa;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

/**
 * Map&lt;String, String&gt;을 JSON 문자열로 변환하는 JPA Converter.
 */
@Slf4j
@Converter
public class StringMapConverter implements AttributeConverter<Map<String, String>, String> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<HashMap<String, String>> TYPE_REF = new TypeReference<>() {};

  @Override
  public String convertToDatabaseColumn(Map<String, String> attribute) {
    if (attribute == null || attribute.isEmpty()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(attribute);
    } catch (JsonProcessingException e) {
      log.warn("Failed to convert map to JSON: {}", e.getMessage());
      return null;
    }
  }

  @Override
  public Map<String, String> convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.readValue(dbData, TYPE_REF);
    } catch (JsonProcessingException e) {
      log.warn("Failed to convert JSON to map: {}", e.getMessage());
      return null;
    }
  }
}
