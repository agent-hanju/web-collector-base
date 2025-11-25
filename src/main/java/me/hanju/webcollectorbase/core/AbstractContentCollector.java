package me.hanju.webcollectorbase.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import me.hanju.webcollectorbase.core.dto.ContentCollectedResult;

/**
 * ID 목록 기반 본문 수집을 위한 추상 클래스.
 * <p>
 * {@link #processContent(Object)}와 {@link #saveBatch()}를 구현하여 사용합니다.
 * </p>
 *
 * @param <T> ID 타입
 */
public abstract class AbstractContentCollector<T> implements BatchExecutionConfig {

  /**
   * 단일 ID에 대한 본문을 처리합니다.
   *
   * @param id 처리할 ID
   */
  protected abstract void processContent(T id);

  /**
   * 현재까지 수집된 데이터를 저장합니다.
   */
  protected abstract void saveBatch();

  /**
   * 본문을 수집합니다.
   *
   * @param ids       ID 목록
   * @param batchSize 배치 크기
   * @return 수집 결과
   */
  public final ContentCollectedResult collect(List<T> ids, int batchSize) {
    return collect(ids, batchSize, ICollectorLogger.noOp());
  }

  /**
   * 본문을 수집합니다.
   *
   * @param ids       ID 목록
   * @param batchSize 배치 크기
   * @param logger    로거
   * @return 수집 결과
   */
  public final ContentCollectedResult collect(List<T> ids, int batchSize, ICollectorLogger logger) {
    final int totalCount = ids.size();
    final AtomicInteger successCount = new AtomicInteger(0);
    final AtomicInteger failureCount = new AtomicInteger(0);
    final AtomicInteger batchNumber = new AtomicInteger(0);

    logger.onStart(totalCount, totalCount);

    try {
      final List<CompletableFuture<Void>> futures = new ArrayList<>();
      int index = 0;

      for (T id : ids) {
        if (isShutdownRequested()) {
          break;
        }

        final int currentIndex = ++index;
        final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
          try {
            processContent(id);
            successCount.incrementAndGet();
            logger.onUnitSuccess(currentIndex, 1);
          } catch (Exception e) {
            failureCount.incrementAndGet();
            logger.onUnitFail(currentIndex, e);
          }
        }, getExecutor());
        futures.add(future);

        // 배치 크기마다 저장
        if (futures.size() >= batchSize) {
          CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
          futures.clear();
          saveBatchWithLogging(batchNumber.incrementAndGet(), successCount.get(), logger);
        }
      }

      // 남은 작업 처리
      if (!futures.isEmpty()) {
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        saveBatchWithLogging(batchNumber.incrementAndGet(), successCount.get(), logger);
      }

      logger.onComplete(totalCount, totalCount, failureCount.get(), successCount.get());

    } catch (Exception e) {
      logger.onError(totalCount, totalCount, successCount.get(), failureCount.get(), successCount.get(), e);
    }

    return new ContentCollectedResult(totalCount, successCount.get(), failureCount.get());
  }

  private void saveBatchWithLogging(int batch, int itemCount, ICollectorLogger logger) {
    try {
      saveBatch();
      logger.onBatchSuccess(batch, itemCount);
    } catch (Exception e) {
      logger.onBatchFail(batch, e);
    }
  }
}
