package me.hanju.webcollectorbase.core;

/**
 * 수집 진행 상황을 로깅하기 위한 인터페이스.
 */
public interface ICollectorLogger {

  /** 수집 시작 시 호출 */
  void onStart(Integer totalUnit, Integer totalItem);

  /** 단위(페이지/ID) 수집 성공 시 호출 */
  void onUnitSuccess(Integer unit, Integer itemCount);

  /** 단위 수집 실패 시 호출 */
  void onUnitFail(Integer unit, Exception e);

  /** 배치 저장 성공 시 호출 */
  void onBatchSuccess(Integer batch, Integer itemCount);

  /** 배치 저장 실패 시 호출 */
  void onBatchFail(Integer batch, Exception e);

  /** 수집 완료 시 호출 */
  void onComplete(Integer totalUnit, Integer totalItem, Integer failureCount, Integer successItemCount);

  /** 치명적 오류 시 호출 */
  void onError(Integer totalUnit, Integer totalItem, Integer successUnitCount, Integer failureUnitCount,
      Integer successItemCount, Exception e);

  /** 아무 동작도 하지 않는 No-op Logger */
  static ICollectorLogger noOp() {
    return NoOpLogger.INSTANCE;
  }
}

/** No-op 구현 */
enum NoOpLogger implements ICollectorLogger {
  INSTANCE;

  @Override
  public void onStart(Integer totalUnit, Integer totalItem) {
    // no operation
  }

  @Override
  public void onUnitSuccess(Integer unit, Integer itemCount) {
    // no operation
  }

  @Override
  public void onUnitFail(Integer unit, Exception e) {
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
  public void onComplete(Integer totalUnit, Integer totalItem, Integer failureCount, Integer successItemCount) {
    // no operation
  }

  @Override
  public void onError(Integer totalUnit, Integer totalItem, Integer successUnitCount, Integer failureUnitCount,
      Integer successItemCount, Exception e) {
    // no operation
  }
}
