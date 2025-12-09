package me.hanju.webcollectorbase.jpa;

import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 도메인 엔티티 베이스 클래스
 *
 * @deprecated 0.2.2부터 deprecated. JPA 엔티티는 프로젝트별로 직접 구현하세요.
 *             0.2.5에서 삭제될 예정입니다.
 */
@Deprecated(since = "0.2.2", forRemoval = true)
@SuperBuilder
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@MappedSuperclass
public abstract class AbstractDomainEntity {
  @Builder.Default
  @Column(name = "collected")
  private boolean collected = false;

  /**
   * 해당 데이터가 수집 완료되었음을 알린다.
   */
  public void completeCollected() {
    this.collected = true;
  }

  @Column(name = "unexpected_field_map", columnDefinition = "TEXT")
  @Convert(converter = StringMapConverter.class)
  private Map<String, String> unexpectedFieldMap;

  /**
   * 예상치 못한 필드를 기록합니다.
   *
   * @param key   필드명
   * @param value 직렬화된 값
   */
  public void putUnexpectedField(final String key, final String value) {
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
