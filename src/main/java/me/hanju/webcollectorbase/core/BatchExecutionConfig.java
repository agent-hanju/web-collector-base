package me.hanju.webcollectorbase.core;

import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

import jakarta.annotation.Nullable;

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
   * 배치 플러시 작업 실행에 사용할 Executor를 반환시키십시오.
   * 기본적으론 순차 실행으로 동작합니다.
   *
   * @return flush용 Executor (기본: 순차 실행)
   */
  default Executor getFlushExecutor() {
    return Runnable::run;
  }

  /**
   * 동시 접근 제한을 위한 Semaphore를 반환시키십시오.
   * 기본적으론 null을 반환하며 접근 제한을 하지 않습니다.
   *
   * @return Semaphore 또는 null (기본: null, 제한 없음)
   */
  @Nullable
  default Semaphore getSemaphore() {
    return null;
  }

  /**
   * Graceful shutdown이 요청되었을 때 true를 반환하도록 구현하십시오.
   * 기본적으론 항상 false를 반환합니다.
   *
   * @return shutdown 요청 여부 (기본: false)
   */
  default boolean isShutdownRequested() {
    return false;
  }

  /**
   * Graceful shutdown을 요청하도록 구현하십시오.
   * 기본적으론 아무런 동작도 하지 않습니다.
   */
  default void requestShutdown() {
    // no-op by default
  }

  /**
   * {@link#getSemaphore()}를 사용하여 작업을 실행합니다.
   *
   * @param action 실행할 작업
   */
  default void withSemaphore(final Runnable action) {
    final Semaphore semaphore = getSemaphore();
    if (semaphore == null) {
      action.run();
    } else {
      try {
        semaphore.acquire();
        try {
          action.run();
        } finally {
          semaphore.release();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new BatchExecutionException("Interrupted while waiting for semaphore", e);
      }
    }
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
