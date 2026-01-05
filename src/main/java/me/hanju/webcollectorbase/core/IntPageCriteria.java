package me.hanju.webcollectorbase.core;

/**
 * 페이지 번호만 포함하는 기본 검색 조건.
 * <p>
 * 단순히 페이지 번호로만 조회하는 경우에 사용합니다.
 * 복잡한 검색 조건이 필요하면 {@link PageCriteria}를 직접 구현하세요.
 * </p>
 *
 * @param page 페이지 번호
 */
public record IntPageCriteria(int page) implements PageCriteria<IntPageCriteria> {

  @Override
  public IntPageCriteria ofPage(int page) {
    return new IntPageCriteria(page);
  }
}
