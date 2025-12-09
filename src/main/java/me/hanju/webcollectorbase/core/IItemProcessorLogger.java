package me.hanju.webcollectorbase.core;

/**
 * 아이템 기반 배치 처리 진행 상황을 로깅하기 위한 인터페이스.
 * <p>
 * 전체 크기를 미리 알 수 없을 수 있으므로,
 * {@link #onStart(Long)}의 totalCount 파라미터는 nullable이며 카운터는 Long 타입을 사용합니다.
 * </p>
 */
public interface IItemProcessorLogger {

  /**
   * 처리 시작 시 호출됩니다.
   *
   * @param totalCount 전체 처리 대상 수 (null이면 모름)
   */
  void onStart(Long totalCount);

  /** 개별 아이템 처리 성공 시 호출 */
  void onItemSuccess(Long index);

  /** 개별 아이템 처리 실패 시 호출 */
  void onItemFail(Long index, Exception e);

  /** 배치 읽기 완료 시 호출 */
  void onBatchFetched(Integer batch, Integer itemCount);

  /** 배치 저장 성공 시 호출 */
  void onBatchSuccess(Integer batch, Long processedCount);

  /** 배치 저장 실패 시 호출 */
  void onBatchFail(Integer batch, Exception e);

  /** 처리 완료 시 호출 */
  void onComplete(Long totalProcessed, Long successCount, Long failureCount);

  /** 치명적 오류 시 호출 */
  void onError(Long totalProcessed, Long successCount, Long failureCount, Exception e);

  /** 아무 동작도 하지 않는 No-op Logger */
  static IItemProcessorLogger noOp() {
    return NoOpItemProcessorLogger.INSTANCE;
  }
}

/** No-op 구현 */
enum NoOpItemProcessorLogger implements IItemProcessorLogger {
  INSTANCE;

  @Override
  public void onStart(Long totalCount) {
    // no operation
  }

  @Override
  public void onItemSuccess(Long index) {
    // no operation
  }

  @Override
  public void onItemFail(Long index, Exception e) {
    // no operation
  }

  @Override
  public void onBatchFetched(Integer batch, Integer itemCount) {
    // no operation
  }

  @Override
  public void onBatchSuccess(Integer batch, Long processedCount) {
    // no operation
  }

  @Override
  public void onBatchFail(Integer batch, Exception e) {
    // no operation
  }

  @Override
  public void onComplete(Long totalProcessed, Long successCount, Long failureCount) {
    // no operation
  }

  @Override
  public void onError(Long totalProcessed, Long successCount, Long failureCount, Exception e) {
    // no operation
  }
}
