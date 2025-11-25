package me.hanju.webcollectorbase.jpa;

import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/**
 * 도메인 엔티티 베이스 클래스.
 * <p>
 * unexpectedFieldMap을 제공합니다.
 * </p>
 */
@Getter
@MappedSuperclass
public abstract class AbstractDomainEntity {

  @Column(name = "unexpected_field_map", columnDefinition = "TEXT")
  @Convert(converter = StringMapConverter.class)
  private Map<String, String> unexpectedFieldMap;

  /**
   * 예상치 못한 필드를 기록합니다.
   *
   * @param key   필드명
   * @param value 직렬화된 값
   */
  public void putUnexpectedField(String key, String value) {
    if (key != null) {
      final Map<String, String> newMap;
      if (unexpectedFieldMap == null) {
        newMap = new HashMap<>();
      } else {
        newMap = new HashMap<>(this.unexpectedFieldMap);
      }
      newMap.put(key, value);
      this.unexpectedFieldMap = newMap;
    }
  }
}
