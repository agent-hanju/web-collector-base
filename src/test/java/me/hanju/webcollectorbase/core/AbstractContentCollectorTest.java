package me.hanju.webcollectorbase.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.hanju.webcollectorbase.core.dto.ContentCollectedResult;

class AbstractContentCollectorTest {

  @Test
  @DisplayName("모든 ID 수집 성공")
  void collectAllIds() {
    List<Long> collected = new ArrayList<>();

    AbstractContentCollector<Long> collector = new AbstractContentCollector<>() {
      @Override
      protected void processContent(Long id) {
        collected.add(id);
      }

      @Override
      protected void saveBatch() {
        // no-op
      }
    };

    List<Long> ids = List.of(1L, 2L, 3L, 4L, 5L);
    ContentCollectedResult result = collector.collect(ids, 10);

    assertEquals(5, result.totalCount());
    assertEquals(5, result.successCount());
    assertEquals(0, result.failureCount());
    assertEquals(5, collected.size());
  }

  @Test
  @DisplayName("일부 ID 처리 실패")
  void handleContentException() {
    AbstractContentCollector<Long> collector = new AbstractContentCollector<>() {
      @Override
      protected void processContent(Long id) {
        if (id == 3L) {
          throw new RuntimeException("Test exception");
        }
      }

      @Override
      protected void saveBatch() {
        // no-op
      }
    };

    List<Long> ids = List.of(1L, 2L, 3L, 4L, 5L);
    ContentCollectedResult result = collector.collect(ids, 10);

    assertEquals(5, result.totalCount());
    assertEquals(4, result.successCount());
    assertEquals(1, result.failureCount());
  }

  @Test
  @DisplayName("빈 ID 목록 처리")
  void collectEmptyList() {
    AbstractContentCollector<Long> collector = new AbstractContentCollector<>() {
      @Override
      protected void processContent(Long id) {
      }

      @Override
      protected void saveBatch() {
      }
    };

    ContentCollectedResult result = collector.collect(List.of(), 10);

    assertEquals(0, result.totalCount());
    assertEquals(0, result.successCount());
    assertEquals(0, result.failureCount());
  }

  @Test
  @DisplayName("배치 크기마다 saveBatch 호출")
  void saveBatchCalledPerBatchSize() {
    List<Integer> batchCalls = new ArrayList<>();

    AbstractContentCollector<Long> collector = new AbstractContentCollector<>() {
      @Override
      protected void processContent(Long id) {
      }

      @Override
      protected void saveBatch() {
        batchCalls.add(1);
      }
    };

    List<Long> ids = List.of(1L, 2L, 3L, 4L, 5L);
    collector.collect(ids, 2);

    // 5개 ID, 배치 크기 2 → 3번 호출 (2, 2, 1)
    assertEquals(3, batchCalls.size());
  }

  @Test
  @DisplayName("비동기 모드: 모든 배치 저장 성공")
  void asyncSave_allBatchesSaved() {
    List<Integer> savedBatches = Collections.synchronizedList(new ArrayList<>());
    AtomicInteger processCount = new AtomicInteger(0);

    AbstractContentCollector<Long> collector = new AbstractContentCollector<>() {
      @Override
      protected void processContent(Long id) {
        processCount.incrementAndGet();
      }

      @Override
      protected void saveBatch() {
        savedBatches.add(1);
        // 저장 지연 시뮬레이션
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

    List<Long> ids = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
    ContentCollectedResult result = collector.collect(ids, 3);

    // 10개 ID, 배치 크기 3 -> 4번 저장 (3, 3, 3, 1)
    assertEquals(4, savedBatches.size());
    assertEquals(10, result.successCount());
    assertEquals(10, processCount.get());
  }

  @Test
  @DisplayName("비동기 모드: 저장 실패해도 수집 계속 진행")
  void asyncSave_continuesOnSaveFailure() {
    AtomicInteger processCount = new AtomicInteger(0);
    AtomicInteger saveCallCount = new AtomicInteger(0);

    AbstractContentCollector<Long> collector = new AbstractContentCollector<>() {
      @Override
      protected void processContent(Long id) {
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
        return Runnable::run; // 동기지만 비동기 경로 테스트
      }
    };

    List<Long> ids = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
    collector.collect(ids, 3);

    // 저장 실패와 무관하게 모든 ID 처리됨
    assertEquals(10, processCount.get());
  }

  @Test
  @DisplayName("비동기 모드: collect() 반환 전 모든 저장 완료 보장")
  void asyncSave_allSavesCompletedBeforeReturn() {
    AtomicBoolean allSavesCompleted = new AtomicBoolean(false);
    AtomicInteger saveCount = new AtomicInteger(0);

    AbstractContentCollector<Long> collector = new AbstractContentCollector<>() {
      @Override
      protected void processContent(Long id) {
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

    List<Long> ids = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
    collector.collect(ids, 3); // 3번 저장 호출

    // collect() 반환 시점에 모든 저장이 완료되어야 함
    assertTrue(allSavesCompleted.get());
  }

  @Test
  @DisplayName("기본값(순차 실행): getExecutor 오버라이드 없이 순차 처리")
  void defaultExecutor_sequentialProcessing() {
    List<String> executionOrder = new ArrayList<>();

    AbstractContentCollector<Long> collector = new AbstractContentCollector<>() {
      @Override
      protected void processContent(Long id) {
        executionOrder.add("process-" + id);
      }

      @Override
      protected void saveBatch() {
        executionOrder.add("flush");
      }

      // getExecutor() 오버라이드 안함 → 기본값 Runnable::run (순차 실행)
    };

    List<Long> ids = List.of(1L, 2L, 3L, 4L, 5L);
    collector.collect(ids, 2); // 배치 2

    // 순차 실행이므로 순서가 보장됨
    System.out.println("실행 순서: " + executionOrder);

    // 첫 flush가 process-3보다 먼저 실행되어야 함 (순차 실행 증명)
    int firstFlushIndex = executionOrder.indexOf("flush");
    int process3Index = executionOrder.indexOf("process-3");
    assertTrue(firstFlushIndex < process3Index,
        "순차 실행에서는 flush가 process-3보다 먼저 실행되어야 함");

    assertEquals("process-1", executionOrder.get(0));
  }

  @Test
  @DisplayName("backpressure: 동시 flush 개수가 maxPendingFlushes를 초과하지 않음")
  void backpressure_limitsConcurrentFlushes() {
    AtomicInteger concurrentFlushes = new AtomicInteger(0);
    AtomicInteger maxConcurrentFlushes = new AtomicInteger(0);

    AbstractContentCollector<Long> collector = new AbstractContentCollector<>() {
      @Override
      protected void processContent(Long id) {
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

    // 20개 ID, 배치 3 → 7번 flush
    List<Long> ids = new ArrayList<>();
    for (long i = 1; i <= 20; i++) {
      ids.add(i);
    }
    collector.collect(ids, 3);

    assertTrue(maxConcurrentFlushes.get() <= 2,
        "동시 flush 개수가 maxPendingFlushes를 초과함: " + maxConcurrentFlushes.get());
  }

  @Test
  @DisplayName("maxPendingFlushes(기본값 3) 초과 시 flush 완료까지 다음 배치 시작 대기")
  void flushCompletionUnblocksNextBatch() {
    AtomicInteger flushNumber = new AtomicInteger(0);
    AtomicInteger concurrentFlushes = new AtomicInteger(0);
    AtomicInteger maxConcurrentFlushes = new AtomicInteger(0);
    List<String> timeline = Collections.synchronizedList(new ArrayList<>());
    long testStartTime = System.currentTimeMillis();

    AbstractContentCollector<Long> collector = new AbstractContentCollector<>() {
      @Override
      protected void processContent(Long id) {
      }

      @Override
      protected void saveBatch() {
        int myFlushNumber = flushNumber.incrementAndGet();
        int concurrent = concurrentFlushes.incrementAndGet();
        maxConcurrentFlushes.updateAndGet(max -> Math.max(max, concurrent));
        long elapsed = System.currentTimeMillis() - testStartTime;

        timeline.add(String.format("[%4dms] flush-%d START (concurrent=%d)", elapsed, myFlushNumber, concurrent));

        try {
          Thread.sleep(200);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          elapsed = System.currentTimeMillis() - testStartTime;
          concurrentFlushes.decrementAndGet();
          timeline.add(String.format("[%4dms] flush-%d END", elapsed, myFlushNumber));
        }
      }

      @Override
      public Executor getExecutor() {
        return Executors.newFixedThreadPool(6);
      }

      // getMaxPendingFlushes() 오버라이드 안함 → 기본값 3 사용
    };

    // 16개 ID, 배치 4 → 4번 flush
    List<Long> ids = new ArrayList<>();
    for (long i = 1; i <= 16; i++) {
      ids.add(i);
    }
    collector.collect(ids, 4);

    System.out.println("=== ContentCollector Flush 타임라인 (maxPendingFlushes=3) ===");
    timeline.forEach(System.out::println);
    System.out.println("최대 동시 flush: " + maxConcurrentFlushes.get());

    assertTrue(maxConcurrentFlushes.get() <= 3,
        "동시 flush가 maxPendingFlushes(3)를 초과함: " + maxConcurrentFlushes.get());
  }

  @Test
  @DisplayName("빠른 처리 + 느린 저장: backpressure로 제어")
  void fastProcessingSlowFlush_backpressureControlsQueue() {
    AtomicInteger concurrentFlushes = new AtomicInteger(0);
    AtomicInteger maxConcurrentFlushes = new AtomicInteger(0);
    AtomicInteger completedFlushes = new AtomicInteger(0);

    AbstractContentCollector<Long> collector = new AbstractContentCollector<>() {
      @Override
      protected void processContent(Long id) {
        // 빠른 처리
      }

      @Override
      protected void saveBatch() {
        int current = concurrentFlushes.incrementAndGet();
        maxConcurrentFlushes.updateAndGet(max -> Math.max(max, current));

        try {
          Thread.sleep(300); // 느린 저장
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          concurrentFlushes.decrementAndGet();
          completedFlushes.incrementAndGet();
        }
      }

      @Override
      public Executor getExecutor() {
        return Executors.newFixedThreadPool(8);
      }

      @Override
      public int getMaxPendingFlushes() {
        return 2;
      }
    };

    // 30개 ID, 배치 5 → 6번 flush
    List<Long> ids = new ArrayList<>();
    for (long i = 1; i <= 30; i++) {
      ids.add(i);
    }

    long startTime = System.currentTimeMillis();
    ContentCollectedResult result = collector.collect(ids, 5);
    long totalTime = System.currentTimeMillis() - startTime;

    assertEquals(30, result.successCount());
    assertTrue(maxConcurrentFlushes.get() <= 2,
        "동시 flush가 2를 초과: " + maxConcurrentFlushes.get());

    // 6번 flush, 각 300ms, 동시 2개 → 최소 900ms
    assertTrue(totalTime >= 800, "backpressure로 인해 시간이 늘어나야 함: " + totalTime + "ms");

    System.out.println("=== ContentCollector 처리-저장 불균형 테스트 ===");
    System.out.println("총 소요시간: " + totalTime + "ms");
    System.out.println("최대 동시 flush: " + maxConcurrentFlushes.get());
    System.out.println("완료된 flush: " + completedFlushes.get());
  }
}
