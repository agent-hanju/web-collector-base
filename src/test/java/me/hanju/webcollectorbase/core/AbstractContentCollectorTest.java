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
      public Executor getFlushExecutor() {
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
      public Executor getFlushExecutor() {
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
      public Executor getFlushExecutor() {
        return Executors.newFixedThreadPool(2);
      }
    };

    List<Long> ids = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
    collector.collect(ids, 3); // 3번 저장 호출

    // collect() 반환 시점에 모든 저장이 완료되어야 함
    assertTrue(allSavesCompleted.get());
  }

  @Test
  @DisplayName("동기 모드(기본값): 기존 동작과 동일")
  void syncSave_backwardCompatible() {
    List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());

    AbstractContentCollector<Long> collector = new AbstractContentCollector<>() {
      @Override
      protected void processContent(Long id) {
        executionOrder.add("process-" + id);
      }

      @Override
      protected void saveBatch() {
        executionOrder.add("save");
      }

      // getSaveExecutor() 오버라이드 안함 -> 기본값 null (동기 실행)
    };

    List<Long> ids = List.of(1L, 2L, 3L);
    collector.collect(ids, 2);

    // 동기 모드: save가 process-3보다 먼저 실행됨
    int saveIndex = executionOrder.indexOf("save");
    int process3Index = executionOrder.indexOf("process-3");
    assertTrue(saveIndex < process3Index, "동기 모드에서는 save가 process-3보다 먼저 실행되어야 함");
  }
}
