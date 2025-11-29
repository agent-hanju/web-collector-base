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

import me.hanju.webcollectorbase.core.dto.ListCollectedResult;
import me.hanju.webcollectorbase.core.dto.PageInfo;

class AbstractListCollectorTest {

  @Test
  @DisplayName("순차 실행으로 모든 페이지 수집")
  void collectAllPages() {
    List<Integer> collected = new ArrayList<>();

    AbstractListCollector collector = new AbstractListCollector() {
      @Override
      protected PageInfo processPage(int page) {
        collected.add(page);
        return new PageInfo(3, 30, 10);
      }

      @Override
      protected void saveBatch() {
        // no-op
      }
    };

    ListCollectedResult result = collector.collect(10);

    assertEquals(3, result.totalPage());
    assertEquals(30, result.totalItem());
    assertEquals(3, result.successPageCount());
    assertEquals(0, result.failurePageCount());
    assertEquals(30, result.successItemCount());
    assertEquals(3, collected.size());
  }

  @Test
  @DisplayName("단일 페이지만 있는 경우")
  void collectSinglePage() {
    AbstractListCollector collector = new AbstractListCollector() {
      @Override
      protected PageInfo processPage(int page) {
        return new PageInfo(1, 5, 5);
      }

      @Override
      protected void saveBatch() {
        // no-op
      }
    };

    ListCollectedResult result = collector.collect(10);

    assertEquals(1, result.totalPage());
    assertEquals(5, result.totalItem());
    assertEquals(1, result.successPageCount());
  }

  @Test
  @DisplayName("페이지 처리 중 예외 발생 시 실패 카운트 증가")
  void handlePageException() {
    AbstractListCollector collector = new AbstractListCollector() {
      @Override
      protected PageInfo processPage(int page) {
        if (page == 2) {
          throw new RuntimeException("Test exception");
        }
        return new PageInfo(3, 30, 10);
      }

      @Override
      protected void saveBatch() {
        // no-op
      }
    };

    ListCollectedResult result = collector.collect(10);

    assertEquals(3, result.totalPage());
    assertEquals(2, result.successPageCount());
    assertEquals(1, result.failurePageCount());
  }

  @Test
  @DisplayName("shutdown 요청 시 수집 중단")
  void stopOnShutdown() {
    List<Integer> collected = new ArrayList<>();

    AbstractListCollector collector = new AbstractListCollector() {
      private boolean shutdown = false;

      @Override
      protected PageInfo processPage(int page) {
        collected.add(page);
        if (page == 2) {
          shutdown = true;
        }
        return new PageInfo(10, 100, 10);
      }

      @Override
      protected void saveBatch() {
      }

      @Override
      public boolean isShutdownRequested() {
        return shutdown;
      }
    };

    collector.collect(10);

    // 첫 페이지 + 2페이지까지 처리 후 중단 (3페이지부터 중단)
    assertEquals(2, collected.size());
  }

  @Test
  @DisplayName("비동기 모드: 모든 배치 저장 성공")
  void asyncSave_allBatchesSaved() {
    List<Integer> savedBatches = Collections.synchronizedList(new ArrayList<>());
    AtomicInteger processCount = new AtomicInteger(0);

    AbstractListCollector collector = new AbstractListCollector() {
      @Override
      protected PageInfo processPage(int page) {
        processCount.incrementAndGet();
        return new PageInfo(10, 100, 10);
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

    ListCollectedResult result = collector.collect(3);

    // 10페이지, 배치 크기 3 -> 4번 저장 (첫페이지 포함: 1 + 9/3 = 4번)
    assertEquals(3, savedBatches.size());
    assertEquals(10, result.successPageCount());
    assertEquals(10, processCount.get());
  }

  @Test
  @DisplayName("비동기 모드: 저장 실패해도 수집 계속 진행")
  void asyncSave_continuesOnSaveFailure() {
    AtomicInteger processCount = new AtomicInteger(0);
    AtomicInteger saveCallCount = new AtomicInteger(0);

    AbstractListCollector collector = new AbstractListCollector() {
      @Override
      protected PageInfo processPage(int page) {
        processCount.incrementAndGet();
        return new PageInfo(10, 100, 10);
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

    collector.collect(3);

    // 저장 실패와 무관하게 모든 페이지 처리됨
    assertEquals(10, processCount.get());
  }

  @Test
  @DisplayName("비동기 모드: collect() 반환 전 모든 저장 완료 보장")
  void asyncSave_allSavesCompletedBeforeReturn() {
    AtomicBoolean allSavesCompleted = new AtomicBoolean(false);
    AtomicInteger saveCount = new AtomicInteger(0);

    AbstractListCollector collector = new AbstractListCollector() {
      @Override
      protected PageInfo processPage(int page) {
        return new PageInfo(7, 70, 10);
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

    collector.collect(2); // 7페이지, 배치 2 -> 3번 저장 호출

    // collect() 반환 시점에 모든 저장이 완료되어야 함
    assertTrue(allSavesCompleted.get());
  }

  @Test
  @DisplayName("기본값(순차 실행): getExecutor 오버라이드 없이 순차 처리")
  void defaultExecutor_sequentialProcessing() {
    List<String> executionOrder = new ArrayList<>();

    AbstractListCollector collector = new AbstractListCollector() {
      @Override
      protected PageInfo processPage(int page) {
        executionOrder.add("process-" + page);
        return new PageInfo(5, 50, 10);
      }

      @Override
      protected void saveBatch() {
        executionOrder.add("flush");
      }

      // getExecutor() 오버라이드 안함 → 기본값 Runnable::run (순차 실행)
    };

    collector.collect(2); // 배치 2

    // 순차 실행이므로 순서가 보장됨:
    // process-1 → process-2, process-3 → flush → process-4, process-5 → flush
    System.out.println("실행 순서: " + executionOrder);

    // 첫 flush가 process-4보다 먼저 실행되어야 함 (순차 실행 증명)
    int firstFlushIndex = executionOrder.indexOf("flush");
    int process4Index = executionOrder.indexOf("process-4");
    assertTrue(firstFlushIndex < process4Index,
        "순차 실행에서는 flush가 process-4보다 먼저 실행되어야 함");

    // 모든 페이지가 순서대로 처리됨
    assertEquals("process-1", executionOrder.get(0));
  }

  @Test
  @DisplayName("backpressure: 동시 flush 개수가 maxPendingFlushes를 초과하지 않음")
  void backpressure_limitsConcurrentFlushes() {
    AtomicInteger concurrentFlushes = new AtomicInteger(0);
    AtomicInteger maxConcurrentFlushes = new AtomicInteger(0);

    AbstractListCollector collector = new AbstractListCollector() {
      @Override
      protected PageInfo processPage(int page) {
        return new PageInfo(20, 200, 10); // 20페이지
      }

      @Override
      protected void saveBatch() {
        int current = concurrentFlushes.incrementAndGet();
        maxConcurrentFlushes.updateAndGet(max -> Math.max(max, current));
        try {
          Thread.sleep(50); // flush 지연
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          concurrentFlushes.decrementAndGet();
        }
      }

      @Override
      public Executor getExecutor() {
        return Executors.newFixedThreadPool(10); // 충분히 많은 스레드
      }

      @Override
      public int getMaxPendingFlushes() {
        return 2; // 동시 flush 최대 2개
      }
    };

    collector.collect(3); // 20페이지, 배치 3 -> 여러 번 flush

    // 동시 flush가 maxPendingFlushes(2)를 초과하지 않아야 함
    assertTrue(maxConcurrentFlushes.get() <= 2,
        "동시 flush 개수가 maxPendingFlushes를 초과함: " + maxConcurrentFlushes.get());
  }
}
