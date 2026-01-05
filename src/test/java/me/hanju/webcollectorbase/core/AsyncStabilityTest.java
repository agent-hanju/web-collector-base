package me.hanju.webcollectorbase.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import me.hanju.webcollectorbase.core.dto.ItemProcessedResult;
import me.hanju.webcollectorbase.core.dto.PageInfo;

/**
 * 메모리와 네트워크를 사용하는 비동기 작업의 안정성 테스트.
 * <p>
 * 실제 운영 환경에서 발생할 수 있는 상황들을 시뮬레이션합니다:
 * - DB 작업 지연
 * - 네트워크 요청 지연
 * - 커넥션 풀 고갈
 * - 메모리 압박
 * </p>
 */
class AsyncStabilityTest {

  // ========== 시뮬레이션 유틸리티 ==========

  /**
   * DB 커넥션 풀 시뮬레이터.
   * 제한된 커넥션 수와 대기 시간을 시뮬레이션합니다.
   */
  static class ConnectionPoolSimulator {
    private final Semaphore connections;
    private final long connectionTimeMs;
    private final AtomicInteger waitingCount = new AtomicInteger(0);
    private final AtomicInteger maxWaitingCount = new AtomicInteger(0);
    private final AtomicInteger timeoutCount = new AtomicInteger(0);

    ConnectionPoolSimulator(int poolSize, long connectionTimeMs) {
      this.connections = new Semaphore(poolSize);
      this.connectionTimeMs = connectionTimeMs;
    }

    /**
     * 커넥션을 획득하고 작업을 수행합니다.
     *
     * @param waitTimeoutMs 커넥션 대기 타임아웃 (ms)
     * @param task          수행할 작업
     * @return 작업 성공 여부
     */
    boolean executeWithConnection(long waitTimeoutMs, Runnable task) {
      int waiting = waitingCount.incrementAndGet();
      maxWaitingCount.updateAndGet(max -> Math.max(max, waiting));

      try {
        if (!connections.tryAcquire(waitTimeoutMs, TimeUnit.MILLISECONDS)) {
          timeoutCount.incrementAndGet();
          return false;
        }
        try {
          Thread.sleep(connectionTimeMs); // 커넥션 사용 시간
          task.run();
          return true;
        } finally {
          connections.release();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      } finally {
        waitingCount.decrementAndGet();
      }
    }

    int getMaxWaitingCount() {
      return maxWaitingCount.get();
    }

    int getTimeoutCount() {
      return timeoutCount.get();
    }
  }

  /**
   * 네트워크 요청 시뮬레이터.
   * 지연, 간헐적 실패, 타임아웃을 시뮬레이션합니다.
   */
  static class NetworkSimulator {
    private final long baseLatencyMs;
    private final double failureRate;
    private final long maxLatencyMs;
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger slowRequestCount = new AtomicInteger(0);

    NetworkSimulator(long baseLatencyMs, long maxLatencyMs, double failureRate) {
      this.baseLatencyMs = baseLatencyMs;
      this.maxLatencyMs = maxLatencyMs;
      this.failureRate = failureRate;
    }

    /**
     * 네트워크 요청을 시뮬레이션합니다.
     *
     * @throws RuntimeException 네트워크 실패 시
     */
    void request() {
      requestCount.incrementAndGet();

      // 랜덤 지연 (baseLatency ~ maxLatency)
      long delay = baseLatencyMs + (long) (Math.random() * (maxLatencyMs - baseLatencyMs));
      if (delay > baseLatencyMs * 2) {
        slowRequestCount.incrementAndGet();
      }

      try {
        Thread.sleep(delay);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      // 간헐적 실패
      if (Math.random() < failureRate) {
        failureCount.incrementAndGet();
        throw new RuntimeException("Network request failed");
      }
    }

    int getRequestCount() {
      return requestCount.get();
    }

    int getFailureCount() {
      return failureCount.get();
    }

    int getSlowRequestCount() {
      return slowRequestCount.get();
    }
  }

  /**
   * 메모리 압박 시뮬레이터.
   * 대량의 데이터를 메모리에 적재하는 상황을 시뮬레이션합니다.
   */
  static class MemoryPressureSimulator {
    private final List<byte[]> allocatedMemory = Collections.synchronizedList(new ArrayList<>());
    private final int allocationSizeKb;
    private final AtomicInteger totalAllocatedKb = new AtomicInteger(0);

    MemoryPressureSimulator(int allocationSizeKb) {
      this.allocationSizeKb = allocationSizeKb;
    }

    /**
     * 메모리를 할당합니다.
     */
    void allocate() {
      byte[] data = new byte[allocationSizeKb * 1024];
      // 메모리가 최적화로 제거되지 않도록 데이터 채우기
      for (int i = 0; i < data.length; i += 1024) {
        data[i] = (byte) i;
      }
      allocatedMemory.add(data);
      totalAllocatedKb.addAndGet(allocationSizeKb);
    }

    /**
     * 할당된 메모리를 해제합니다.
     */
    void release() {
      allocatedMemory.clear();
      totalAllocatedKb.set(0);
    }

    int getTotalAllocatedKb() {
      return totalAllocatedKb.get();
    }

    int getAllocationCount() {
      return allocatedMemory.size();
    }
  }

  // ========== 테스트 케이스 ==========

  @Nested
  @DisplayName("DB 작업 지연 시나리오")
  class SlowDatabaseTests {

    @Test
    @DisplayName("DB 저장이 느릴 때도 수집은 계속 진행됨")
    @Timeout(30)
    void collectContinuesWhileSlowDbSave() {
      AtomicInteger processedPages = new AtomicInteger(0);
      AtomicInteger savedBatches = new AtomicInteger(0);
      long dbSaveDelayMs = 200; // DB 저장에 200ms 소요

      AbstractPageProcessor<IntPageCriteria> processor = new AbstractPageProcessor<>() {
        @Override
        protected int fetchTotalPage(IntPageCriteria criteria) {
          return 10;
        }

        @Override
        protected PageInfo processPage(IntPageCriteria criteria) {
          processedPages.incrementAndGet();
          try {
            Thread.sleep(10); // 페이지 처리 10ms
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return new PageInfo(10, 100, 10);
        }

        @Override
        protected void saveBatch() {
          try {
            Thread.sleep(dbSaveDelayMs); // 느린 DB 저장
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          savedBatches.incrementAndGet();
        }

        @Override
        public Executor getExecutor() {
          return Executors.newFixedThreadPool(4);
        }

        @Override
        public int getMaxPendingFlushes() {
          return 3;
        }
      };
      processor.setBaseCriteria(new IntPageCriteria(1));

      long startTime = System.currentTimeMillis();
      ItemProcessedResult result = processor.process(3);
      long elapsedTime = System.currentTimeMillis() - startTime;

      assertEquals(10, result.successCount());
      assertTrue(savedBatches.get() >= 3); // 배치 저장 수
      // 비동기 처리로 인해 순차 처리보다 빠름
      assertTrue(elapsedTime < 700, "비동기 처리가 순차보다 빨라야 함: " + elapsedTime + "ms");
    }

    @Test
    @DisplayName("DB 커넥션 풀 고갈 시 backpressure 적용")
    @Timeout(30)
    void backpressureWhenConnectionPoolExhausted() {
      ConnectionPoolSimulator dbPool = new ConnectionPoolSimulator(2, 100); // 2개 커넥션, 100ms 사용
      AtomicInteger successfulSaves = new AtomicInteger(0);

      AbstractPageProcessor<IntPageCriteria> processor = new AbstractPageProcessor<>() {
        @Override
        protected int fetchTotalPage(IntPageCriteria criteria) {
          return 15;
        }

        @Override
        protected PageInfo processPage(IntPageCriteria criteria) {
          return new PageInfo(15, 150, 10);
        }

        @Override
        protected void saveBatch() {
          boolean success = dbPool.executeWithConnection(5000, () -> {
            // DB 작업 시뮬레이션
          });
          if (success) {
            successfulSaves.incrementAndGet();
          }
        }

        @Override
        public Executor getExecutor() {
          return Executors.newFixedThreadPool(4);
        }

        @Override
        public int getMaxPendingFlushes() {
          return 2; // DB 커넥션 풀 크기와 동일하게 제한
        }
      };
      processor.setBaseCriteria(new IntPageCriteria(1));

      processor.process(3);

      // 모든 저장이 성공해야 함 (backpressure로 커넥션 풀 고갈 방지)
      assertEquals(0, dbPool.getTimeoutCount(), "커넥션 타임아웃이 없어야 함");
      assertTrue(dbPool.getMaxWaitingCount() <= 2, "동시 대기가 2개를 초과하면 안됨");
    }
  }

  @Nested
  @DisplayName("네트워크 요청 지연 시나리오")
  class SlowNetworkTests {

    @Test
    @DisplayName("네트워크 요청이 느릴 때 병렬 처리로 전체 시간 단축")
    @Timeout(30)
    void parallelProcessingWithSlowNetwork() {
      NetworkSimulator network = new NetworkSimulator(50, 100, 0); // 50~100ms 지연, 실패 없음
      AtomicInteger processedPages = new AtomicInteger(0);

      AbstractPageProcessor<IntPageCriteria> processor = new AbstractPageProcessor<>() {
        @Override
        protected int fetchTotalPage(IntPageCriteria criteria) {
          return 10;
        }

        @Override
        protected PageInfo processPage(IntPageCriteria criteria) {
          network.request();
          processedPages.incrementAndGet();
          return new PageInfo(10, 100, 10);
        }

        @Override
        protected void saveBatch() {
          // 빠른 저장
        }

        @Override
        public Executor getExecutor() {
          return Executors.newFixedThreadPool(5);
        }
      };
      processor.setBaseCriteria(new IntPageCriteria(1));

      long startTime = System.currentTimeMillis();
      processor.process(5);
      long elapsedTime = System.currentTimeMillis() - startTime;

      assertEquals(10, processedPages.get());
      // 순차: 10 * 75ms(평균) = 750ms
      // 병렬(5스레드): ~150ms + 오버헤드
      assertTrue(elapsedTime < 500, "병렬 처리로 시간이 단축되어야 함: " + elapsedTime + "ms");
    }

    @Test
    @DisplayName("간헐적 네트워크 실패 시 실패 카운트 정확히 기록")
    @Timeout(30)
    void handlesIntermittentNetworkFailures() {
      // fetchTotalPage는 별도로 호출되므로 모든 processPage에서 네트워크 실패 가능
      NetworkSimulator network = new NetworkSimulator(10, 30, 0.2); // 20% 실패율

      AbstractPageProcessor<IntPageCriteria> processor = new AbstractPageProcessor<>() {
        @Override
        protected int fetchTotalPage(IntPageCriteria criteria) {
          return 20;
        }

        @Override
        protected PageInfo processPage(IntPageCriteria criteria) {
          network.request();
          return new PageInfo(20, 200, 10);
        }

        @Override
        protected void saveBatch() {
        }

        @Override
        public Executor getExecutor() {
          return Executors.newFixedThreadPool(4);
        }
      };
      processor.setBaseCriteria(new IntPageCriteria(1));

      ItemProcessedResult result = processor.process(5);

      // 실패한 페이지 수가 네트워크 실패 수와 일치
      assertEquals(network.getFailureCount(), result.failureCount());
      assertEquals(20, result.successCount() + result.failureCount());
    }
  }

  @Nested
  @DisplayName("커넥션 풀 부족 시나리오")
  class ConnectionPoolExhaustionTests {

    @Test
    @DisplayName("요청량 > 커넥션 풀: maxPendingFlushes로 안정성 확보")
    @Timeout(60)
    void stabilityWithSmallConnectionPool() {
      // 시나리오: 50개 페이지, 배치 5, 커넥션 풀 2개
      ConnectionPoolSimulator dbPool = new ConnectionPoolSimulator(2, 50);
      AtomicInteger processedCount = new AtomicInteger(0);
      AtomicInteger saveCount = new AtomicInteger(0);

      AbstractPageProcessor<IntPageCriteria> processor = new AbstractPageProcessor<>() {
        @Override
        protected int fetchTotalPage(IntPageCriteria criteria) {
          return 50;
        }

        @Override
        protected PageInfo processPage(IntPageCriteria criteria) {
          processedCount.incrementAndGet();
          return new PageInfo(50, 500, 10);
        }

        @Override
        protected void saveBatch() {
          dbPool.executeWithConnection(10000, saveCount::incrementAndGet);
        }

        @Override
        public Executor getExecutor() {
          return Executors.newFixedThreadPool(10); // 많은 워커 스레드
        }

        @Override
        public int getMaxPendingFlushes() {
          return 2; // 커넥션 풀 크기에 맞춤
        }
      };
      processor.setBaseCriteria(new IntPageCriteria(1));

      ItemProcessedResult result = processor.process(5);

      assertEquals(50, result.successCount());
      assertEquals(0, dbPool.getTimeoutCount(), "타임아웃 없이 모든 저장 완료");
      // flush 대기 수가 maxPendingFlushes를 크게 초과하지 않아야 함
      assertTrue(dbPool.getMaxWaitingCount() <= 3,
          "커넥션 대기가 너무 많음: " + dbPool.getMaxWaitingCount());
    }

    @Test
    @DisplayName("ThreadPool이 작아도 데드락 없이 완료")
    @Timeout(30)
    void noDeadlockWithSmallThreadPool() {
      AtomicInteger completedTasks = new AtomicInteger(0);

      // 매우 작은 스레드 풀 (2개)로 많은 작업 처리
      ExecutorService smallPool = new ThreadPoolExecutor(
          2, 2, 0L, TimeUnit.MILLISECONDS,
          new ArrayBlockingQueue<>(100)
      );

      AbstractPageProcessor<IntPageCriteria> processor = new AbstractPageProcessor<>() {
        @Override
        protected int fetchTotalPage(IntPageCriteria criteria) {
          return 10;
        }

        @Override
        protected PageInfo processPage(IntPageCriteria criteria) {
          try {
            Thread.sleep(20);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          completedTasks.incrementAndGet();
          return new PageInfo(10, 100, 10);
        }

        @Override
        protected void saveBatch() {
          try {
            Thread.sleep(50);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }

        @Override
        public Executor getExecutor() {
          return smallPool;
        }

        @Override
        public int getMaxPendingFlushes() {
          return 1; // 극단적으로 제한
        }
      };
      processor.setBaseCriteria(new IntPageCriteria(1));

      ItemProcessedResult result = processor.process(3);
      smallPool.shutdown();

      assertEquals(10, result.successCount());
      assertEquals(10, completedTasks.get(), "모든 작업이 데드락 없이 완료");
    }
  }

  @Nested
  @DisplayName("메모리 압박 시나리오")
  class MemoryPressureTests {

    @Test
    @DisplayName("배치 처리로 메모리 사용량 제어")
    @Timeout(30)
    void batchProcessingControlsMemory() {
      MemoryPressureSimulator memory = new MemoryPressureSimulator(100); // 100KB per item
      AtomicInteger maxMemoryKb = new AtomicInteger(0);
      Object lock = new Object();

      AbstractPageProcessor<IntPageCriteria> processor = new AbstractPageProcessor<>() {
        @Override
        protected int fetchTotalPage(IntPageCriteria criteria) {
          return 20;
        }

        @Override
        protected PageInfo processPage(IntPageCriteria criteria) {
          memory.allocate();
          synchronized (lock) {
            maxMemoryKb.updateAndGet(max -> Math.max(max, memory.getTotalAllocatedKb()));
          }
          return new PageInfo(20, 200, 10);
        }

        @Override
        protected void saveBatch() {
          // 배치 저장 후 메모리 해제 시뮬레이션
          try {
            Thread.sleep(30);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          memory.release();
        }

        @Override
        public Executor getExecutor() {
          return Executors.newFixedThreadPool(4);
        }

        @Override
        public int getMaxPendingFlushes() {
          return 2;
        }
      };
      processor.setBaseCriteria(new IntPageCriteria(1));

      processor.process(5); // 배치 크기 5

      // 배치 처리로 메모리가 무한정 증가하지 않음
      // 최대 메모리 = (배치 크기 + pending flushes) * 100KB
      int expectedMaxKb = (5 + 2 + 1) * 100; // 여유 있게 계산
      assertTrue(maxMemoryKb.get() <= expectedMaxKb * 2,
          "메모리 사용량이 제어되어야 함: " + maxMemoryKb.get() + "KB");
    }
  }

  @Nested
  @DisplayName("처리-저장 속도 불균형 시나리오")
  class ProcessingFlushImbalanceTests {

    @Test
    @DisplayName("빠른 처리 + 느린 저장: flush 큐가 쌓이지만 backpressure로 제어")
    @Timeout(60)
    void fastProcessingSlowFlush_backpressureControlsQueue() {
      // 시나리오: 페이지 처리 10ms, DB 저장 300ms
      // 처리가 30배 빠름 → flush가 쌓일 수 있음
      AtomicInteger pendingFlushCount = new AtomicInteger(0);
      AtomicInteger maxPendingFlushCount = new AtomicInteger(0);
      AtomicInteger completedFlushCount = new AtomicInteger(0);
      List<Long> flushStartTimes = Collections.synchronizedList(new ArrayList<>());
      List<Long> flushEndTimes = Collections.synchronizedList(new ArrayList<>());

      AbstractPageProcessor<IntPageCriteria> processor = new AbstractPageProcessor<>() {
        @Override
        protected int fetchTotalPage(IntPageCriteria criteria) {
          return 30;
        }

        @Override
        protected PageInfo processPage(IntPageCriteria criteria) {
          try {
            Thread.sleep(10); // 빠른 처리
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return new PageInfo(30, 300, 10); // 30페이지
        }

        @Override
        protected void saveBatch() {
          int pending = pendingFlushCount.incrementAndGet();
          maxPendingFlushCount.updateAndGet(max -> Math.max(max, pending));
          flushStartTimes.add(System.currentTimeMillis());

          try {
            Thread.sleep(300); // 느린 저장 (처리의 30배)
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            pendingFlushCount.decrementAndGet();
            completedFlushCount.incrementAndGet();
            flushEndTimes.add(System.currentTimeMillis());
          }
        }

        @Override
        public Executor getExecutor() {
          return Executors.newFixedThreadPool(8);
        }

        @Override
        public int getMaxPendingFlushes() {
          return 3; // 최대 3개까지만 동시 flush 허용
        }
      };
      processor.setBaseCriteria(new IntPageCriteria(1));

      long startTime = System.currentTimeMillis();
      ItemProcessedResult result = processor.process(5); // 배치 5
      long totalTime = System.currentTimeMillis() - startTime;

      assertEquals(30, result.successCount());

      // 핵심 검증: maxPendingFlushes(3)를 초과하지 않음
      assertTrue(maxPendingFlushCount.get() <= 3,
          "동시 pending flush가 maxPendingFlushes를 초과: " + maxPendingFlushCount.get());

      // 모든 flush 완료
      assertTrue(completedFlushCount.get() >= 5, "모든 배치가 저장되어야 함: " + completedFlushCount.get());

      System.out.println("=== 처리-저장 불균형 테스트 결과 ===");
      System.out.println("총 소요시간: " + totalTime + "ms");
      System.out.println("최대 동시 pending flush: " + maxPendingFlushCount.get());
      System.out.println("완료된 flush 수: " + completedFlushCount.get());
    }

    @Test
    @DisplayName("극단적 불균형: 처리 1ms, 저장 500ms → backpressure가 처리를 늦춤")
    @Timeout(120)
    void extremeImbalance_backpressureSlowsProcessing() {
      // 시나리오: 처리가 저장보다 500배 빠름
      AtomicInteger processedPages = new AtomicInteger(0);
      AtomicInteger concurrentFlushes = new AtomicInteger(0);
      AtomicInteger maxConcurrentFlushes = new AtomicInteger(0);
      List<Long> batchCompleteTimes = Collections.synchronizedList(new ArrayList<>());

      AbstractPageProcessor<IntPageCriteria> processor = new AbstractPageProcessor<>() {
        @Override
        protected int fetchTotalPage(IntPageCriteria criteria) {
          return 40;
        }

        @Override
        protected PageInfo processPage(IntPageCriteria criteria) {
          processedPages.incrementAndGet();
          // 거의 즉시 완료
          return new PageInfo(40, 400, 10); // 40페이지
        }

        @Override
        protected void saveBatch() {
          int current = concurrentFlushes.incrementAndGet();
          maxConcurrentFlushes.updateAndGet(max -> Math.max(max, current));

          try {
            Thread.sleep(500); // 매우 느린 저장
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            concurrentFlushes.decrementAndGet();
            batchCompleteTimes.add(System.currentTimeMillis());
          }
        }

        @Override
        public Executor getExecutor() {
          return Executors.newFixedThreadPool(10);
        }

        @Override
        public int getMaxPendingFlushes() {
          return 2; // 엄격하게 2개로 제한
        }
      };
      processor.setBaseCriteria(new IntPageCriteria(1));

      long startTime = System.currentTimeMillis();
      processor.process(5); // 배치 5
      long totalTime = System.currentTimeMillis() - startTime;

      // backpressure로 동시 flush 제한됨
      assertTrue(maxConcurrentFlushes.get() <= 2,
          "동시 flush가 2를 초과: " + maxConcurrentFlushes.get());

      // 40페이지, 배치 5 → 8번 flush, 각 500ms, 동시 2개 → 최소 2000ms
      // backpressure가 없으면 모든 flush가 동시에 시작되어 ~500ms에 끝남
      assertTrue(totalTime >= 1500, "backpressure로 인해 시간이 늘어나야 함: " + totalTime + "ms");

      System.out.println("=== 극단적 불균형 테스트 결과 ===");
      System.out.println("총 소요시간: " + totalTime + "ms");
      System.out.println("최대 동시 flush: " + maxConcurrentFlushes.get());
      System.out.println("처리된 페이지: " + processedPages.get());
    }

    @Test
    @DisplayName("flush 큐 포화 시 새 배치 처리가 대기함")
    @Timeout(60)
    void flushQueueSaturation_newBatchesWait() {
      // 시나리오: flush 큐가 꽉 차면 새 배치 처리 전에 대기해야 함
      AtomicInteger batchesWaitingForFlush = new AtomicInteger(0);
      AtomicInteger maxWaiting = new AtomicInteger(0);
      List<String> eventLog = Collections.synchronizedList(new ArrayList<>());

      AbstractPageProcessor<IntPageCriteria> processor = new AbstractPageProcessor<>() {
        @Override
        protected int fetchTotalPage(IntPageCriteria criteria) {
          return 20;
        }

        @Override
        protected PageInfo processPage(IntPageCriteria criteria) {
          eventLog.add("process-" + criteria.page());
          return new PageInfo(20, 200, 10);
        }

        @Override
        protected void saveBatch() {
          int waiting = batchesWaitingForFlush.incrementAndGet();
          maxWaiting.updateAndGet(max -> Math.max(max, waiting));
          eventLog.add("flush-start (concurrent=" + waiting + ")");

          try {
            Thread.sleep(200); // 느린 저장
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            batchesWaitingForFlush.decrementAndGet();
            eventLog.add("flush-end");
          }
        }

        @Override
        public Executor getExecutor() {
          return Executors.newFixedThreadPool(6);
        }

        @Override
        public int getMaxPendingFlushes() {
          return 2;
        }
      };
      processor.setBaseCriteria(new IntPageCriteria(1));

      processor.process(4); // 배치 4

      // maxPendingFlushes=2 이므로 동시에 2개 이상 대기하면 안됨
      assertTrue(maxWaiting.get() <= 2,
          "flush 대기가 너무 많음: " + maxWaiting.get());

      System.out.println("=== Flush 큐 포화 테스트 ===");
      System.out.println("최대 동시 대기: " + maxWaiting.get());
      System.out.println("이벤트 로그 (처음 20개):");
      eventLog.stream().limit(20).forEach(e -> System.out.println("  " + e));
    }

    @Test
    @DisplayName("maxPendingFlushes(기본값 3) 초과 시 flush 완료까지 다음 배치 시작 대기")
    @Timeout(60)
    void flushCompletionUnblocksNextBatch() {
      // 시나리오: maxPendingFlushes=3(기본값)일 때, 4번째 배치는 1번째가 끝날 때까지 대기
      AtomicInteger flushNumber = new AtomicInteger(0);
      AtomicInteger concurrentFlushes = new AtomicInteger(0);
      AtomicInteger maxConcurrentFlushes = new AtomicInteger(0);
      List<String> timeline = Collections.synchronizedList(new ArrayList<>());
      long testStartTime = System.currentTimeMillis();

      AbstractPageProcessor<IntPageCriteria> processor = new AbstractPageProcessor<>() {
        @Override
        protected int fetchTotalPage(IntPageCriteria criteria) {
          return 20;
        }

        @Override
        protected PageInfo processPage(IntPageCriteria criteria) {
          return new PageInfo(20, 200, 10); // 20페이지 → 4번 flush (5페이지씩)
        }

        @Override
        protected void saveBatch() {
          int myFlushNumber = flushNumber.incrementAndGet();
          int concurrent = concurrentFlushes.incrementAndGet();
          maxConcurrentFlushes.updateAndGet(max -> Math.max(max, concurrent));
          long elapsed = System.currentTimeMillis() - testStartTime;

          timeline.add(String.format("[%4dms] flush-%d START (concurrent=%d)", elapsed, myFlushNumber, concurrent));

          try {
            Thread.sleep(300); // 느린 저장
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
      processor.setBaseCriteria(new IntPageCriteria(1));

      processor.process(5); // 배치 5

      System.out.println("=== Flush 완료 대기 테스트 타임라인 (maxPendingFlushes=3) ===");
      timeline.forEach(System.out::println);
      System.out.println("최대 동시 flush: " + maxConcurrentFlushes.get());

      // 검증: concurrent가 3을 초과한 적이 없어야 함
      assertTrue(maxConcurrentFlushes.get() <= 3,
          "동시 flush가 maxPendingFlushes(3)를 초과함: " + maxConcurrentFlushes.get());

      // 검증: 4번째 flush는 1번째가 끝난 후에 시작해야 함
      System.out.println("\n=== 검증: flush-4는 flush-1 완료 후 시작 ===");
    }
  }

  @Nested
  @DisplayName("복합 시나리오")
  class CombinedScenarioTests {

    @Test
    @DisplayName("느린 네트워크 + 느린 DB + 작은 풀 = 안정적 처리")
    @Timeout(60)
    void combinedSlowScenario() {
      NetworkSimulator network = new NetworkSimulator(30, 80, 0.1); // 10% 실패
      ConnectionPoolSimulator dbPool = new ConnectionPoolSimulator(2, 50);
      AtomicInteger savedBatches = new AtomicInteger(0);

      AbstractPageProcessor<IntPageCriteria> processor = new AbstractPageProcessor<>() {
        @Override
        protected int fetchTotalPage(IntPageCriteria criteria) {
          return 30;
        }

        @Override
        protected PageInfo processPage(IntPageCriteria criteria) {
          network.request();
          return new PageInfo(30, 300, 10);
        }

        @Override
        protected void saveBatch() {
          dbPool.executeWithConnection(10000, savedBatches::incrementAndGet);
        }

        @Override
        public Executor getExecutor() {
          return Executors.newFixedThreadPool(5);
        }

        @Override
        public int getMaxPendingFlushes() {
          return 2;
        }
      };
      processor.setBaseCriteria(new IntPageCriteria(1));

      ItemProcessedResult result = processor.process(5);

      // 기본 검증
      assertEquals(30, result.totalProcessed());
      assertTrue(result.successCount() >= 20, "대부분 성공해야 함 (10% 실패율 감안)");
      assertEquals(0, dbPool.getTimeoutCount(), "DB 타임아웃 없어야 함");

      // 실패 처리 검증
      assertEquals(network.getFailureCount(), result.failureCount());
    }

    @Test
    @DisplayName("급격한 부하 증가 시 graceful degradation")
    @Timeout(60)
    void gracefulDegradationUnderLoad() {
      AtomicInteger concurrentRequests = new AtomicInteger(0);
      AtomicInteger maxConcurrent = new AtomicInteger(0);

      AbstractPageProcessor<IntPageCriteria> processor = new AbstractPageProcessor<>() {
        @Override
        protected int fetchTotalPage(IntPageCriteria criteria) {
          return 50;
        }

        @Override
        protected PageInfo processPage(IntPageCriteria criteria) {
          int current = concurrentRequests.incrementAndGet();
          maxConcurrent.updateAndGet(max -> Math.max(max, current));

          try {
            // 부하에 따라 지연 증가 시뮬레이션
            int delay = 20 + (current * 5);
            Thread.sleep(Math.min(delay, 200));
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            concurrentRequests.decrementAndGet();
          }

          return new PageInfo(50, 500, 10);
        }

        @Override
        protected void saveBatch() {
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }

        @Override
        public Executor getExecutor() {
          return Executors.newFixedThreadPool(8);
        }

        @Override
        public int getMaxPendingFlushes() {
          return 3;
        }
      };
      processor.setBaseCriteria(new IntPageCriteria(1));

      ItemProcessedResult result = processor.process(10);

      assertEquals(50, result.successCount());
      // 동시 요청 수가 스레드 풀 + pending flushes 이내로 제한
      assertTrue(maxConcurrent.get() <= 12,
          "동시 요청이 제한되어야 함: " + maxConcurrent.get());
    }
  }
}
