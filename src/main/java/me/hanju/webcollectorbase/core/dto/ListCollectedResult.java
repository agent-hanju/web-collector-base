package me.hanju.webcollectorbase.core.dto;

/**
 * 목록 수집 결과.
 *
 * @param totalPage 전체 페이지 수
 * @param totalItem 전체 아이템 수
 * @param successPageCount 성공한 페이지 수
 * @param failurePageCount 실패한 페이지 수
 * @param successItemCount 성공적으로 수집된 아이템 수
 */
public record ListCollectedResult(
    Integer totalPage,
    Integer totalItem,
    Integer successPageCount,
    Integer failurePageCount,
    Integer successItemCount) {
}
