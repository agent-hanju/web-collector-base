package me.hanju.webcollectorbase.jpa;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * API 응답 raw data 저장을 위한 베이스 엔티티.
 * <p>
 * 대리키(id)와 raw JSON data, 수집 시각을 제공합니다.
 * </p>
 */
@SuperBuilder
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@MappedSuperclass
public abstract class AbstractResponseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Setter
  @Column(name = "raw_data", columnDefinition = "LONGTEXT")
  private String rawData;

  @Column(name = "recorded_at")
  private Instant recordedAt;

  @PrePersist
  public void prePersist() {
    this.recordedAt = Instant.now();
  }

  @PreUpdate
  public void preUpdate() {
    this.recordedAt = Instant.now();
  }
}
