package me.hanju.webcollectorbase.core;

/**
 * 페이지 기반 목록 수집 진행 상황을 로깅하기 위한 인터페이스.
 */
public interface IListCollectorLogger {

  /** 수집 시작 시 호출 */
  void onStart(Integer totalPage, Integer totalItem);

  /** 페이지 수집 성공 시 호출 */
  void onPageSuccess(Integer page, Integer itemCount);

  /** 페이지 수집 실패 시 호출 */
  void onPageFail(Integer page, Exception e);

  /** 배치 저장 성공 시 호출 */
  void onBatchSuccess(Integer batch, Integer itemCount);

  /** 배치 저장 실패 시 호출 */
  void onBatchFail(Integer batch, Exception e);

  /** 수집 완료 시 호출 */
  void onComplete(Integer totalPage, Integer totalItem, Integer failureCount, Integer successItemCount);

  /** 치명적 오류 시 호출 */
  void onError(Integer totalPage, Integer totalItem, Integer successPageCount, Integer failurePageCount,
      Integer successItemCount, Exception e);

  /** 아무 동작도 하지 않는 No-op Logger */
  static IListCollectorLogger noOp() {
    return NoOpListCollectorLogger.INSTANCE;
  }
}

/** No-op 구현 */
enum NoOpListCollectorLogger implements IListCollectorLogger {
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
