package me.hanju.webcollectorbase.core.dto;

/**
 * 본문 수집 결과.
 *
 * @param totalCount 전체 ID 수
 * @param successCount 성공한 ID 수
 * @param failureCount 실패한 ID 수
 * @deprecated 0.2.2부터 deprecated.
 *             {@link ItemProcessedResult}를 사용하세요.
 *             0.2.5에서 삭제될 예정입니다.
 */
@Deprecated(since = "0.2.2", forRemoval = true)
public record ContentCollectedResult(
    Integer totalCount,
    Integer successCount,
    Integer failureCount) {
}
