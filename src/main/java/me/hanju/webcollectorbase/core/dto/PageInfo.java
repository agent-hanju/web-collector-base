package me.hanju.webcollectorbase.core.dto;

/**
 * 페이지 수집 결과 정보.
 *
 * @param totalPage 전체 페이지 수
 * @param totalItem 전체 아이템 수
 * @param itemCount 현재 페이지에서 수집된 아이템 수
 */
public record PageInfo(Integer totalPage, Integer totalItem, Integer itemCount) {
}
