package me.hanju.webcollectorbase.core.dto;

/**
 * 아이템 처리 결과.
 *
 * @param totalProcessed 총 처리 시도 수
 * @param successCount   성공 수
 * @param failureCount   실패 수
 */
public record ItemProcessedResult(
    Long totalProcessed,
    Long successCount,
    Long failureCount) {
}
