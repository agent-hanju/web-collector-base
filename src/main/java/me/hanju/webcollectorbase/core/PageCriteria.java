package me.hanju.webcollectorbase.core;

/**
 * 페이지 검색 조건을 정의하는 인터페이스.
 * <p>
 * {@link AbstractPageProcessor}에서 페이지를 조회할 때 필요한 검색 조건을 담습니다.
 * 구현 클래스에서 URL 파라미터, API 요청 조건, DB 쿼리 조건 등을 정의합니다.
 * </p>
 *
 * <pre>{@code
 * // 구현 예시
 * public class ArticleSearchCriteria implements PageCriteria<ArticleSearchCriteria> {
 *     private final String keyword;
 *     private final LocalDate startDate;
 *     private final int page;
 *
 *     @Override
 *     public ArticleSearchCriteria ofPage(int page) {
 *         return new ArticleSearchCriteria(keyword, startDate, page);
 *     }
 *
 *     public String toUrl() {
 *         return "/api/articles?keyword=" + keyword + "&page=" + page;
 *     }
 * }
 * }</pre>
 *
 * @param <T> 구현 클래스 타입 (자기 자신)
 */
public interface PageCriteria<T extends PageCriteria<T>> {

  /**
   * 지정된 페이지 번호로 새로운 검색 조건을 생성합니다.
   * <p>
   * 현재 조건을 유지하면서 페이지 번호만 변경된 새 객체를 반환합니다.
   * </p>
   *
   * @param page 페이지 번호
   * @return 페이지가 설정된 새 검색 조건
   */
  T ofPage(int page);
}
