package me.hanju.webcollectorbase.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import me.hanju.webcollectorbase.core.dto.PageInfo;
import me.hanju.webcollectorbase.core.dto.ItemProcessedResult;

/**
 * 페이지 기반 배치 처리를 위한 추상 클래스.
 * <p>
 * {@link AbstractItemProcessor}를 상속하여 검색 조건({@link PageCriteria})을 아이템으로 처리합니다.
 * {@link #setBaseCriteria(PageCriteria)}로 검색 조건을 설정하고,
 * {@link #processPage(PageCriteria)}, {@link #saveBatch()}를 구현하여 사용합니다.
 * </p>
 *
 * @param <C> 검색 조건 타입 ({@link PageCriteria} 구현체)
 */
public abstract class AbstractPageProcessor<C extends PageCriteria<C>> extends AbstractItemProcessor<C> {

  private final AtomicInteger currentPage = new AtomicInteger(0);
  private final AtomicInteger totalPage = new AtomicInteger(-1);
  private C baseCriteria;

  /**
   * 기본 검색 조건을 설정합니다.
   * <p>
   * {@link #process(int)} 호출 전에 반드시 설정해야 합니다.
   * </p>
   *
   * @param criteria 기본 검색 조건
   */
  public void setBaseCriteria(C criteria) {
    this.baseCriteria = criteria;
  }

  /**
   * 기본 검색 조건을 반환합니다.
   *
   * @return 기본 검색 조건
   * @throws IllegalStateException 검색 조건이 설정되지 않은 경우
   */
  public C getBaseCriteria() {
    if (baseCriteria == null) {
      throw new IllegalStateException("baseCriteria가 설정되지 않았습니다. setBaseCriteria()를 먼저 호출하세요.");
    }
    return baseCriteria;
  }

  /**
   * 전체 페이지 수를 조회합니다.
   * <p>
   * {@link #process(int)} 호출 시 페이지 처리 전에 호출됩니다.
   * 전체 페이지 수만 조회하는 API를 구현하세요.
   * </p>
   *
   * @param criteria 검색 조건
   * @return 전체 페이지 수
   */
  protected abstract int fetchTotalPage(C criteria);

  /**
   * 검색 조건으로 페이지를 처리하고 결과 정보를 반환합니다.
   *
   * @param criteria 검색 조건
   * @return 페이지 처리 결과 정보
   */
  protected abstract PageInfo processPage(C criteria);

  @Override
  protected final Long getTotalCount() {
    return totalPage.get() > 0 ? (long) totalPage.get() : null;
  }

  @Override
  public ItemProcessedResult process(int batchSize) {
    initTotalPage();
    return super.process(batchSize);
  }

  @Override
  public ItemProcessedResult process(int batchSize, IItemProcessorLogger logger) {
    initTotalPage();
    return super.process(batchSize, logger);
  }

  private void initTotalPage() {
    if (totalPage.get() < 0) {
      totalPage.set(fetchTotalPage(getBaseCriteria().ofPage(1)));
    }
  }

  @Override
  protected final List<C> fetchNextBatch(int batchSize) {
    C base = getBaseCriteria();

    int nextPage = currentPage.incrementAndGet();

    if (nextPage > totalPage.get()) {
      return Collections.emptyList();
    }

    // 다음 batchSize개 페이지에 대한 검색 조건 생성
    List<C> criteria = new ArrayList<>();
    criteria.add(base.ofPage(nextPage));

    for (int i = 1; i < batchSize; i++) {
      nextPage = currentPage.incrementAndGet();
      if (nextPage > totalPage.get()) {
        break;
      }
      criteria.add(base.ofPage(nextPage));
    }

    return criteria;
  }

  @Override
  protected final void processItem(C criteria) {
    processPage(criteria);
  }
}
