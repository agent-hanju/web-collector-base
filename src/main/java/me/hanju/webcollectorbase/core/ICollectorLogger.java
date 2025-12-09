package me.hanju.webcollectorbase.core;

/**
 * 수집 진행 상황을 로깅하기 위한 인터페이스.
 *
 * @deprecated 0.2.2부터 {@link IListCollectorLogger}, {@link IContentCollectorLogger},
 *             {@link IItemProcessorLogger}로 분리되었습니다.
 *             목록 수집에는 {@link IListCollectorLogger}를 사용하세요.
 *             0.2.5에서 삭제될 예정입니다.
 */
@Deprecated(since = "0.2.2", forRemoval = true)
public interface ICollectorLogger extends IListCollectorLogger {

  /** 아무 동작도 하지 않는 No-op Logger */
  static ICollectorLogger noOp() {
    return NoOpCollectorLogger.INSTANCE;
  }
}

/**
 * No-op 구현
 * @deprecated 0.2.2부터 deprecated. 0.2.5에서 삭제될 예정입니다.
 */
@Deprecated(since = "0.2.2", forRemoval = true)
enum NoOpCollectorLogger implements ICollectorLogger {
  INSTANCE;

  @Override
  public void onStart(Integer totalPage, Integer totalItem) {
    // no operation
  }

  @Override
  public void onPageSuccess(Integer page, Integer itemCount) {
    // no operation
  }

  @Override
  public void onPageFail(Integer page, Exception e) {
    // no operation
  }

  @Override
  public void onBatchSuccess(Integer batch, Integer itemCount) {
    // no operation
  }

  @Override
  public void onBatchFail(Integer batch, Exception e) {
    // no operation
  }

  @Override
  public void onComplete(Integer totalPage, Integer totalItem, Integer failureCount, Integer successItemCount) {
    // no operation
  }

  @Override
  public void onError(Integer totalPage, Integer totalItem, Integer successPageCount, Integer failurePageCount,
      Integer successItemCount, Exception e) {
    // no operation
  }
}
