package me.hanju.webcollectorbase.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import me.hanju.webcollectorbase.core.dto.ListCollectedResult;
import me.hanju.webcollectorbase.core.dto.PageInfo;

/**
 * 페이지 단위 목록 수집을 위한 추상 클래스.
 * <p>
 * {@link #processPage(int)}와 {@link #saveBatch()}를 구현하여 사용합니다.
 * </p>
 */
public abstract class AbstractListCollector implements BatchExecutionConfig {

  /**
   * 페이지를 처리하고 결과 정보를 반환합니다.
   *
   * @param page 페이지 번호 (1부터 시작)
   * @return 페이지 처리 결과 정보
   */
  protected abstract PageInfo processPage(int page);

  /**
   * 현재까지 수집된 데이터를 저장합니다.
   */
  protected abstract void saveBatch();

  /**
   * 목록을 수집합니다.
   *
   * @param batchSize 배치 크기
   * @return 수집 결과
   */
  public final ListCollectedResult collect(final int batchSize) {
    return collect(batchSize, IListCollectorLogger.noOp());
  }

  /**
   * 목록을 수집합니다.
   *
   * @param batchSize 배치 크기
   * @param logger    로거
   * @return 수집 결과
   */
  public final ListCollectedResult collect(final int batchSize, final IListCollectorLogger logger) {
    final AtomicInteger totalPage = new AtomicInteger(1);
    final AtomicInteger totalItem = new AtomicInteger(0);
    final AtomicInteger successPageCount = new AtomicInteger(0);
    final AtomicInteger failurePageCount = new AtomicInteger(0);
    final AtomicInteger successItemCount = new AtomicInteger(0);
    final AtomicInteger batchNumber = new AtomicInteger(0);
    final Semaphore semaphore = new Semaphore(getMaxPendingFlushes());
    final List<CompletableFuture<Void>> flushFutures = new ArrayList<>();

    try {
      if (!isShutdownRequested()) {
        // 첫 페이지로 전체 페이지 수 확인
        final PageInfo firstPageInfo = processPage(1);
        totalPage.set(firstPageInfo.totalPage());
        totalItem.set(firstPageInfo.totalItem());
        successPageCount.incrementAndGet();
        successItemCount.addAndGet(firstPageInfo.itemCount());

        logger.onStart(totalPage.get(), totalItem.get());
        logger.onPageSuccess(1, firstPageInfo.itemCount());

        if (totalPage.get() <= 1) {
          flushWithLogging(batchNumber.incrementAndGet(), successItemCount.get(), logger);
          logger.onComplete(totalPage.get(), totalItem.get(), failurePageCount.get(), successItemCount.get());
          return new ListCollectedResult(totalPage.get(), totalItem.get(), successPageCount.get(),
              failurePageCount.get(), successItemCount.get());
        }
      }
      // 나머지 페이지 병렬 처리
      final List<CompletableFuture<Void>> futures = new ArrayList<>();
      for (int page = 2; page <= totalPage.get() && !isShutdownRequested(); page++) {
        final int currentPage = page;
        final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
          try {
            final PageInfo pageInfo = processPage(currentPage);
            successPageCount.incrementAndGet();
            successItemCount.addAndGet(pageInfo.itemCount());
            logger.onPageSuccess(currentPage, pageInfo.itemCount());
          } catch (Exception e) {
            failurePageCount.incrementAndGet();
            logger.onPageFail(currentPage, e);
          }
        }, getExecutor());
        futures.add(future);

        // 배치 크기마다 flush
        if (futures.size() >= batchSize) {
          CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
          futures.clear();

          // backpressure 여유 있을 때까지 대기
          semaphore.acquireUninterruptibly();

          final int currentBatch = batchNumber.incrementAndGet();
          final int currentItemCount = successItemCount.get();
          flushFutures.add(CompletableFuture.runAsync(() -> {
            try {
              flushWithLogging(currentBatch, currentItemCount, logger);
            } finally {
              semaphore.release();
            }
          }, getExecutor()));
        }
      }

      // 남은 작업 처리
      if (!futures.isEmpty()) {
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        semaphore.acquireUninterruptibly();
        final int currentBatch = batchNumber.incrementAndGet();
        final int currentItemCount = successItemCount.get();
        flushFutures.add(CompletableFuture.runAsync(() -> {
          try {
            flushWithLogging(currentBatch, currentItemCount, logger);
          } finally {
            semaphore.release();
          }
        }, getExecutor()));
      }

      // 모든 flush 완료 대기
      CompletableFuture.allOf(flushFutures.toArray(new CompletableFuture[0])).join();

      logger.onComplete(totalPage.get(), totalItem.get(), failurePageCount.get(), successItemCount.get());

    } catch (Exception e) {
      // 에러 발생 시에도 진행 중인 flush 완료 대기 (데이터 손실 방지)
      try {
        CompletableFuture.allOf(flushFutures.toArray(new CompletableFuture[0])).join();
      } catch (Exception ignored) {
        // 로깅은 flushWithLogging에서 이미 처리됨
      }
      logger.onError(totalPage.get(), totalItem.get(), successPageCount.get(),
          failurePageCount.get(), successItemCount.get(), e);
    }

    return new ListCollectedResult(totalPage.get(), totalItem.get(), successPageCount.get(),
        failurePageCount.get(), successItemCount.get());
  }

  private void flushWithLogging(final int batch, final int itemCount, final IListCollectorLogger logger) {
    try {
      saveBatch();
      logger.onBatchSuccess(batch, itemCount);
    } catch (Exception e) {
      logger.onBatchFail(batch, e);
    }
  }

}
