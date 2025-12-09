package me.hanju.webcollectorbase.core;

/**
 * ID 기반 본문 수집 진행 상황을 로깅하기 위한 인터페이스.
 *
 * @deprecated 0.2.2부터 deprecated.
 *             {@link IItemProcessorLogger}를 사용하세요.
 *             0.2.5에서 삭제될 예정입니다.
 */
@Deprecated(since = "0.2.2", forRemoval = true)
public interface IContentCollectorLogger {

  /** 수집 시작 시 호출 */
  void onStart(Integer totalCount);

  /** 개별 아이템 수집 성공 시 호출 */
  void onItemSuccess(Integer index);

  /** 개별 아이템 수집 실패 시 호출 */
  void onItemFail(Integer index, Exception e);

  /** 배치 저장 성공 시 호출 */
  void onBatchSuccess(Integer batch, Integer itemCount);

  /** 배치 저장 실패 시 호출 */
  void onBatchFail(Integer batch, Exception e);

  /** 수집 완료 시 호출 */
  void onComplete(Integer totalCount, Integer successCount, Integer failureCount);

  /** 치명적 오류 시 호출 */
  void onError(Integer totalCount, Integer successCount, Integer failureCount, Exception e);

  /** 아무 동작도 하지 않는 No-op Logger */
  static IContentCollectorLogger noOp() {
    return NoOpContentCollectorLogger.INSTANCE;
  }
}

/** No-op 구현 */
enum NoOpContentCollectorLogger implements IContentCollectorLogger {
  INSTANCE;

  @Override
  public void onStart(Integer totalCount) {
    // no operation
  }

  @Override
  public void onItemSuccess(Integer index) {
    // no operation
  }

  @Override
  public void onItemFail(Integer index, Exception e) {
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
  public void onComplete(Integer totalCount, Integer successCount, Integer failureCount) {
    // no operation
  }

  @Override
  public void onError(Integer totalCount, Integer successCount, Integer failureCount, Exception e) {
    // no operation
  }
}
