package me.hanju.webcollectorbase.core;

import java.util.concurrent.Executor;

/**
 * 배치 실행 설정 인터페이스.
 * <p>
 * Executor, Semaphore, Graceful shutdown 설정을 제공합니다.
 * 모든 메서드에 기본 구현이 제공되어, 필요한 것만 오버라이드하면 됩니다.
 * </p>
 */
public interface BatchExecutionConfig {

  /**
   * 배치 작업 실행에 사용할 Executor를 반환시키십시오.
   * 기본적으론 순차 실행으로 동작합니다.
   *
   * @return Executor (기본: 순차 실행)
   */
  default Executor getExecutor() {
    return Runnable::run;
  }

  /**
   * 동시에 실행시킬 수 있는 배치 작업의 최대 개수를 반환합니다.
   *
   * @return 최대 대기 flush 개수 (기본: 3)
   */
  default int getMaxPendingFlushes() {
    return 3;
  }

  /**
   * Graceful shutdown이 요청된 이후에는 true를 반환시키십시오.
   * 기본적으론 항상 false를 반환합니다.
   *
   * @return shutdown 요청 여부 (기본: false)
   */
  default boolean isShutdownRequested() {
    return false;
  }

  /**
   * Graceful shutdown을 요청시키십시오.
   * 기본적으론 아무런 동작도 하지 않습니다.
   */
  default void requestShutdown() {
    // no-op by default
  }

  /**
   * BatchExecutionConfig에서 발생할 수 있는 런타임 예외에 사용합니다.
   */
  public static class BatchExecutionException extends RuntimeException {
    public BatchExecutionException(String message) {
      super(message);
    }

    public BatchExecutionException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
