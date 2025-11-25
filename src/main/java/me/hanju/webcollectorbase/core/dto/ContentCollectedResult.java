package me.hanju.webcollectorbase.core.dto;

/**
 * 본문 수집 결과.
 *
 * @param totalCount 전체 ID 수
 * @param successCount 성공한 ID 수
 * @param failureCount 실패한 ID 수
 */
public record ContentCollectedResult(
    Integer totalCount,
    Integer successCount,
    Integer failureCount) {
}
