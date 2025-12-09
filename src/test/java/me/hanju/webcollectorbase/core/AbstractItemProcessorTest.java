package me.hanju.webcollectorbase.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.hanju.webcollectorbase.core.dto.ItemProcessedResult;

class AbstractItemProcessorTest {

  @Test
  @DisplayName("모든 아이템 처리 성공")
  void processAllItems() {
    List<Long> processed = Collections.synchronizedList(new ArrayList<>());
    List<Long> sourceData = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);

    AbstractItemProcessor<Long> processor = new AbstractItemProcessor<>() {
      private Iterator<Long> iterator = sourceData.iterator();
      private List<Long> currentBatch = new ArrayList<>();

      @Override
      protected List<Long> fetchNextBatch(int batchSize) {
        currentBatch.clear();
        for (int i = 0; i < batchSize && iterator.hasNext(); i++) {
          currentBatch.add(iterator.next());
        }
        return new ArrayList<>(currentBatch);
      }

      @Override
      protected void processItem(Long item) {
        processed.add(item);
      }

      @Override
      protected void saveBatch() {
        // no-op
      }
    };

    ItemProcessedResult result = processor.process(3);

    assertEquals(10L, result.totalProcessed());
    assertEquals(10L, result.successCount());
    assertEquals(0L, result.failureCount());
    assertEquals(10, processed.size());
  }

  @Test
  @DisplayName("일부 아이템 처리 실패")
  void handleItemException() {
    List<Long> sourceData = List.of(1L, 2L, 3L, 4L, 5L);

    AbstractItemProcessor<Long> processor = new AbstractItemProcessor<>() {
      private Iterator<Long> iterator = sourceData.iterator();

      @Override
      protected List<Long> fetchNextBatch(int batchSize) {
        List<Long> batch = new ArrayList<>();
        for (int i = 0; i < batchSize && iterator.hasNext(); i++) {
          batch.add(iterator.next());
        }
        return batch;
      }

      @Override
      protected void processItem(Long item) {
        if (item == 3L) {
          throw new RuntimeException("Test exception");
        }
      }

      @Override
      protected void saveBatch() {
        // no-op
      }
    };

    ItemProcessedResult result = processor.process(10);

    assertEquals(5L, result.totalProcessed());
    assertEquals(4L, result.successCount());
    assertEquals(1L, result.failureCount());
  }

  @Test
  @DisplayName("빈 소스 데이터 처리")
  void processEmptySource() {
    AbstractItemProcessor<Long> processor = new AbstractItemProcessor<>() {
      @Override
      protected List<Long> fetchNextBatch(int batchSize) {
        return List.of(); // 빈 리스트 반환 -> 즉시 종료
      }

      @Override
      protected void processItem(Long item) {
      }

      @Override
      protected void saveBatch() {
      }
    };

    ItemProcessedResult result = processor.process(10);

    assertEquals(0L, result.totalProcessed());
    assertEquals(0L, result.successCount());
    assertEquals(0L, result.failureCount());
  }

  @Test
  @DisplayName("배치마다 saveBatch 호출")
  void saveBatchCalledPerBatch() {
    AtomicInteger saveCallCount = new AtomicInteger(0);
    List<Long> sourceData = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);

    AbstractItemProcessor<Long> processor = new AbstractItemProcessor<>() {
      private Iterator<Long> iterator = sourceData.iterator();

      @Override
      protected List<Long> fetchNextBatch(int batchSize) {
        List<Long> batch = new ArrayList<>();
        for (int i = 0; i < batchSize && iterator.hasNext(); i++) {
          batch.add(iterator.next());
        }
        return batch;
      }

      @Override
      protected void processItem(Long item) {
      }

      @Override
      protected void saveBatch() {
        saveCallCount.incrementAndGet();
      }
    };

    processor.process(3); // 10개 아이템, 배치 3 -> 4번 호출 (3, 3, 3, 1)

    assertEquals(4, saveCallCount.get());
  }

  @Test
  @DisplayName("비동기 모드: 모든 배치 저장 성공")
  void asyncSave_allBatchesSaved() {
    List<Integer> savedBatches = Collections.synchronizedList(new ArrayList<>());
    AtomicInteger processCount = new AtomicInteger(0);
    List<Long> sourceData = new ArrayList<>();
    for (long i = 1; i <= 10; i++) {
      sourceData.add(i);
    }

    AbstractItemProcessor<Long> processor = new AbstractItemProcessor<>() {
      private Iterator<Long> iterator = sourceData.iterator();

      @Override
      protected List<Long> fetchNextBatch(int batchSize) {
        List<Long> batch = new ArrayList<>();
        for (int i = 0; i < batchSize && iterator.hasNext(); i++) {
          batch.add(iterator.next());
        }
        return batch;
      }

      @Override
      protected void processItem(Long item) {
        processCount.incrementAndGet();
      }

      @Override
      protected void saveBatch() {
        savedBatches.add(1);
        try {
          Thread.sleep(10);
        } catch (InterruptedException ignored) {
        }
      }

      @Override
      public Executor getExecutor() {
        return Executors.newFixedThreadPool(2);
      }
    };

    ItemProcessedResult result = processor.process(3);

    assertEquals(4, savedBatches.size());
    assertEquals(10L, result.successCount());
    assertEquals(10, processCount.get());
  }

  @Test
  @DisplayName("비동기 모드: 저장 실패해도 처리 계속 진행")
  void asyncSave_continuesOnSaveFailure() {
    AtomicInteger processCount = new AtomicInteger(0);
    AtomicInteger saveCallCount = new AtomicInteger(0);
    List<Long> sourceData = new ArrayList<>();
    for (long i = 1; i <= 10; i++) {
      sourceData.add(i);
    }

    AbstractItemProcessor<Long> processor = new AbstractItemProcessor<>() {
      private Iterator<Long> iterator = sourceData.iterator();

      @Override
      protected List<Long> fetchNextBatch(int batchSize) {
        List<Long> batch = new ArrayList<>();
        for (int i = 0; i < batchSize && iterator.hasNext(); i++) {
          batch.add(iterator.next());
        }
        return batch;
      }

      @Override
      protected void processItem(Long item) {
        processCount.incrementAndGet();
      }

      @Override
      protected void saveBatch() {
        int call = saveCallCount.incrementAndGet();
        if (call == 2) {
          throw new RuntimeException("Save failed on batch 2");
        }
      }

      @Override
      public Executor getExecutor() {
        return Runnable::run;
      }
    };

    processor.process(3);

    assertEquals(10, processCount.get());
  }

  @Test
  @DisplayName("비동기 모드: process() 반환 전 모든 저장 완료 보장")
  void asyncSave_allSavesCompletedBeforeReturn() {
    AtomicBoolean allSavesCompleted = new AtomicBoolean(false);
    AtomicInteger saveCount = new AtomicInteger(0);
    List<Long> sourceData = new ArrayList<>();
    for (long i = 1; i <= 9; i++) {
      sourceData.add(i);
    }

    AbstractItemProcessor<Long> processor = new AbstractItemProcessor<>() {
      private Iterator<Long> iterator = sourceData.iterator();

      @Override
      protected List<Long> fetchNextBatch(int batchSize) {
        List<Long> batch = new ArrayList<>();
        for (int i = 0; i < batchSize && iterator.hasNext(); i++) {
          batch.add(iterator.next());
        }
        return batch;
      }

      @Override
      protected void processItem(Long item) {
      }

      @Override
      protected void saveBatch() {
        try {
          Thread.sleep(50);
        } catch (InterruptedException ignored) {
        }
        if (saveCount.incrementAndGet() == 3) {
          allSavesCompleted.set(true);
        }
      }

      @Override
      public Executor getExecutor() {
        return Executors.newFixedThreadPool(2);
      }
    };

    processor.process(3); // 9개 아이템, 배치 3 -> 3번 저장 호출

    assertTrue(allSavesCompleted.get());
  }

  @Test
  @DisplayName("shutdown 요청 시 처리 중단")
  void stopOnShutdown() {
    AtomicInteger batchCount = new AtomicInteger(0);
    List<Long> sourceData = new ArrayList<>();
    for (long i = 1; i <= 100; i++) {
      sourceData.add(i);
    }

    AbstractItemProcessor<Long> processor = new AbstractItemProcessor<>() {
      private Iterator<Long> iterator = sourceData.iterator();
      private boolean shutdown = false;

      @Override
      protected List<Long> fetchNextBatch(int batchSize) {
        List<Long> batch = new ArrayList<>();
        for (int i = 0; i < batchSize && iterator.hasNext(); i++) {
          batch.add(iterator.next());
        }
        if (batchCount.incrementAndGet() >= 2) {
          shutdown = true;
        }
        return batch;
      }

      @Override
      protected void processItem(Long item) {
      }

      @Override
      protected void saveBatch() {
      }

      @Override
      public boolean isShutdownRequested() {
        return shutdown;
      }
    };

    ItemProcessedResult result = processor.process(5);

    // 2번째 배치 후 shutdown -> 총 10개만 처리
    assertEquals(10L, result.totalProcessed());
  }

  @Test
  @DisplayName("backpressure: 동시 flush 개수가 maxPendingFlushes를 초과하지 않음")
  void backpressure_limitsConcurrentFlushes() {
    AtomicInteger concurrentFlushes = new AtomicInteger(0);
    AtomicInteger maxConcurrentFlushes = new AtomicInteger(0);
    List<Long> sourceData = new ArrayList<>();
    for (long i = 1; i <= 30; i++) {
      sourceData.add(i);
    }

    AbstractItemProcessor<Long> processor = new AbstractItemProcessor<>() {
      private Iterator<Long> iterator = sourceData.iterator();

      @Override
      protected List<Long> fetchNextBatch(int batchSize) {
        List<Long> batch = new ArrayList<>();
        for (int i = 0; i < batchSize && iterator.hasNext(); i++) {
          batch.add(iterator.next());
        }
        return batch;
      }

      @Override
      protected void processItem(Long item) {
      }

      @Override
      protected void saveBatch() {
        int current = concurrentFlushes.incrementAndGet();
        maxConcurrentFlushes.updateAndGet(max -> Math.max(max, current));
        try {
          Thread.sleep(50);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          concurrentFlushes.decrementAndGet();
        }
      }

      @Override
      public Executor getExecutor() {
        return Executors.newFixedThreadPool(10);
      }

      @Override
      public int getMaxPendingFlushes() {
        return 2;
      }
    };

    processor.process(3);

    assertTrue(maxConcurrentFlushes.get() <= 2,
        "동시 flush 개수가 maxPendingFlushes를 초과함: " + maxConcurrentFlushes.get());
  }

  @Test
  @DisplayName("커서 기반 DB 조회 시뮬레이션")
  void simulateCursorBasedDbQuery() {
    // DB에서 커서로 데이터를 읽어오는 시나리오 시뮬레이션
    List<String> processedRecords = Collections.synchronizedList(new ArrayList<>());
    List<String> savedRecords = Collections.synchronizedList(new ArrayList<>());

    // 가상 DB 데이터
    List<String> dbRecords = new ArrayList<>();
    for (int i = 1; i <= 25; i++) {
      dbRecords.add("record-" + i);
    }

    AbstractItemProcessor<String> processor = new AbstractItemProcessor<>() {
      private int cursor = 0;

      @Override
      protected List<String> fetchNextBatch(int batchSize) {
        // DB 커서처럼 다음 배치 읽기
        if (cursor >= dbRecords.size()) {
          return List.of();
        }
        int end = Math.min(cursor + batchSize, dbRecords.size());
        List<String> batch = new ArrayList<>(dbRecords.subList(cursor, end));
        cursor = end;
        return batch;
      }

      @Override
      protected void processItem(String item) {
        // 레코드 변환/처리
        processedRecords.add(item + "-processed");
      }

      @Override
      protected void saveBatch() {
        // 처리된 레코드를 대상 DB에 저장
        savedRecords.addAll(processedRecords.subList(
            savedRecords.size(),
            processedRecords.size()));
      }

      @Override
      public Executor getExecutor() {
        return Executors.newFixedThreadPool(4);
      }
    };

    ItemProcessedResult result = processor.process(5);

    assertEquals(25L, result.totalProcessed());
    assertEquals(25L, result.successCount());
    assertEquals(25, processedRecords.size());
    assertTrue(processedRecords.contains("record-1-processed"));
    assertTrue(processedRecords.contains("record-25-processed"));
  }

  @Test
  @DisplayName("getTotalCount 기본값은 null")
  void getTotalCount_defaultIsNull() {
    boolean[] wasNull = { false };

    AbstractItemProcessor<Long> processor = new AbstractItemProcessor<>() {
      @Override
      protected List<Long> fetchNextBatch(int batchSize) {
        return List.of();
      }

      @Override
      protected void processItem(Long item) {
      }

      @Override
      protected void saveBatch() {
      }
    };

    processor.process(10, new IItemProcessorLogger() {
      @Override
      public void onStart(Long totalCount) {
        wasNull[0] = (totalCount == null);
      }

      @Override
      public void onItemSuccess(Long index) {
      }

      @Override
      public void onItemFail(Long index, Exception e) {
      }

      @Override
      public void onBatchFetched(Integer batch, Integer itemCount) {
      }

      @Override
      public void onBatchSuccess(Integer batch, Long processedCount) {
      }

      @Override
      public void onBatchFail(Integer batch, Exception e) {
      }

      @Override
      public void onComplete(Long totalProcessed, Long successCount, Long failureCount) {
      }

      @Override
      public void onError(Long totalProcessed, Long successCount, Long failureCount, Exception e) {
      }
    });

    assertTrue(wasNull[0], "기본 getTotalCount()는 null을 반환해야 함");
  }

  @Test
  @DisplayName("getTotalCount 오버라이드 시 logger.onStart에 전달")
  void getTotalCount_passedToLogger() {
    AtomicLong capturedTotalCount = new AtomicLong(-1);

    AbstractItemProcessor<Long> processor = new AbstractItemProcessor<>() {
      @Override
      protected Long getTotalCount() {
        return 100L;
      }

      @Override
      protected List<Long> fetchNextBatch(int batchSize) {
        return List.of();
      }

      @Override
      protected void processItem(Long item) {
      }

      @Override
      protected void saveBatch() {
      }
    };

    processor.process(10, new IItemProcessorLogger() {
      @Override
      public void onStart(Long totalCount) {
        capturedTotalCount.set(totalCount != null ? totalCount : -1);
      }

      @Override
      public void onItemSuccess(Long index) {
      }

      @Override
      public void onItemFail(Long index, Exception e) {
      }

      @Override
      public void onBatchFetched(Integer batch, Integer itemCount) {
      }

      @Override
      public void onBatchSuccess(Integer batch, Long processedCount) {
      }

      @Override
      public void onBatchFail(Integer batch, Exception e) {
      }

      @Override
      public void onComplete(Long totalProcessed, Long successCount, Long failureCount) {
      }

      @Override
      public void onError(Long totalProcessed, Long successCount, Long failureCount, Exception e) {
      }
    });

    assertEquals(100L, capturedTotalCount.get(), "getTotalCount() 반환값이 onStart에 전달되어야 함");
  }
}
