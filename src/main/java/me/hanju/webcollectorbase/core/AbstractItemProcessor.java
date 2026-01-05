package me.hanju.webcollectorbase.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import me.hanju.webcollectorbase.core.dto.ItemProcessedResult;

/**
 * 아이템 기반 배치 처리를 위한 추상 클래스.
 * <p>
 * {@link #fetchNextBatch(int)}, {@link #processItem(Object)}, {@link #saveBatch()}를 구현하여 사용합니다.
 * </p>
 * <p>
 * 전체 크기를 미리 알 수 없는 DB 커서, JPA Scroll API, Iterator 기반 처리에 적합합니다.
 * 전체 크기를 알 수 있는 경우 {@link #getTotalCount()}를 오버라이드하여 진행률 표시를 지원할 수 있습니다.
 * </p>
 *
 * @param <T> 처리할 아이템 타입
 */
public abstract class AbstractItemProcessor<T> implements BatchExecutionConfig {

  /**
   * 전체 처리 대상 수를 반환합니다. (선택적)
   * <p>
   * 오버라이드하지 않으면 null을 반환합니다. (전체 크기 모름)
   * 오버라이드하면 {@link IItemProcessorLogger#onStart(Long)}에서 진행률 표시가 가능합니다.
   * </p>
   *
   * @return 전체 처리 대상 수, 모르면 null
   */
  protected Long getTotalCount() {
    return null;
  }

  /**
   * 다음 배치를 읽어옵니다.
   * <p>
   * 빈 리스트를 반환하면 처리가 종료됩니다.
   * </p>
   *
   * @param batchSize 읽어올 배치 크기
   * @return 읽어온 아이템 목록 (빈 리스트면 종료)
   */
  protected abstract List<T> fetchNextBatch(int batchSize);

  /**
   * 개별 아이템을 처리합니다.
   *
   * @param item 처리할 아이템
   */
  protected abstract void processItem(T item);

  /**
   * 현재까지 처리된 데이터를 저장합니다.
   */
  protected abstract void saveBatch();

  /**
   * 아이템들을 배치로 처리합니다.
   *
   * @param batchSize 배치 크기
   * @return 처리 결과
   */
  public ItemProcessedResult process(final int batchSize) {
    return process(batchSize, IItemProcessorLogger.noOp());
  }

  /**
   * 아이템들을 배치로 처리합니다.
   *
   * @param batchSize 배치 크기
   * @param logger    로거
   * @return 처리 결과
   */
  public ItemProcessedResult process(final int batchSize, final IItemProcessorLogger logger) {
    final AtomicLong totalProcessed = new AtomicLong(0);
    final AtomicLong successCount = new AtomicLong(0);
    final AtomicLong failureCount = new AtomicLong(0);
    final AtomicInteger batchNumber = new AtomicInteger(0);
    final Semaphore semaphore = new Semaphore(getMaxPendingFlushes());
    final List<CompletableFuture<Void>> flushFutures = new ArrayList<>();

    logger.onStart(getTotalCount());

    try {
      while (!isShutdownRequested()) {
        // 다음 배치 읽기
        final List<T> batch = fetchNextBatch(batchSize);

        // 빈 배치면 종료
        if (batch == null || batch.isEmpty()) {
          break;
        }

        final int currentBatchNumber = batchNumber.incrementAndGet();
        logger.onBatchFetched(currentBatchNumber, batch.size());

        // 배치 내 아이템 병렬 처리
        final List<CompletableFuture<Void>> itemFutures = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
          final T item = batch.get(i);
          final long currentIndex = totalProcessed.incrementAndGet();

          itemFutures.add(CompletableFuture.runAsync(() -> {
            try {
              processItem(item);
              successCount.incrementAndGet();
              logger.onItemSuccess(currentIndex);
            } catch (Exception e) {
              failureCount.incrementAndGet();
              logger.onItemFail(currentIndex, e);
            }
          }, getExecutor()));
        }

        // 현재 배치의 모든 아이템 처리 대기
        CompletableFuture.allOf(itemFutures.toArray(new CompletableFuture[0])).join();

        // 동시 실행 수를 만족시킬 수 있을 때까지 대기
        semaphore.acquireUninterruptibly();
        final long currentProcessedCount = successCount.get();
        flushFutures.add(CompletableFuture.runAsync(() -> {
          try {
            flushWithLogging(currentBatchNumber, currentProcessedCount, logger);
          } finally {
            semaphore.release();
          }
        }, getExecutor()));
      }

      // 모든 flush 완료 대기
      CompletableFuture.allOf(flushFutures.toArray(new CompletableFuture[0])).join();

      logger.onComplete(totalProcessed.get(), successCount.get(), failureCount.get());

    } catch (Exception e) {
      // 에러 발생 시에도 진행 중인 flush 완료 대기 (데이터 손실 방지)
      try {
        CompletableFuture.allOf(flushFutures.toArray(new CompletableFuture[0])).join();
      } catch (Exception ignored) {
        // 로깅은 flushWithLogging에서 이미 처리됨
      }
      logger.onError(totalProcessed.get(), successCount.get(), failureCount.get(), e);
    }

    return new ItemProcessedResult(totalProcessed.get(), successCount.get(), failureCount.get());
  }

  private void flushWithLogging(final int batch, final long processedCount, final IItemProcessorLogger logger) {
    try {
      saveBatch();
      logger.onBatchSuccess(batch, processedCount);
    } catch (Exception e) {
      logger.onBatchFail(batch, e);
    }
  }
}
