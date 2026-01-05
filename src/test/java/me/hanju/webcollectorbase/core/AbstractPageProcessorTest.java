package me.hanju.webcollectorbase.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import me.hanju.webcollectorbase.core.dto.ItemProcessedResult;
import me.hanju.webcollectorbase.core.dto.PageInfo;

/**
 * AbstractPageProcessor 핵심 로직 테스트.
 * <p>
 * fetchTotalPage와 processPage 분리, 페이지 순회 로직을 검증합니다.
 * </p>
 */
class AbstractPageProcessorTest {

  @Nested
  @DisplayName("fetchTotalPage/processPage 분리 검증")
  class SeparationTests {

    @Test
    @DisplayName("fetchTotalPage가 processPage보다 먼저 호출됨")
    void fetchTotalPageCalledBeforeProcessPage() {
      List<String> callOrder = Collections.synchronizedList(new ArrayList<>());

      AbstractPageProcessor<IntPageCriteria> processor = new AbstractPageProcessor<>() {
        @Override
        protected int fetchTotalPage(IntPageCriteria criteria) {
          callOrder.add("fetchTotalPage");
          return 5;
        }

        @Override
        protected PageInfo processPage(IntPageCriteria criteria) {
          callOrder.add("processPage-" + criteria.page());
          return new PageInfo(5, 50, 10);
        }

        @Override
        protected void saveBatch() {
          callOrder.add("saveBatch");
        }
      };
      processor.setBaseCriteria(new IntPageCriteria(1));

      processor.process(2);

      // fetchTotalPage가 가장 먼저 호출되어야 함
      assertEquals("fetchTotalPage", callOrder.get(0), "fetchTotalPage가 첫 번째로 호출되어야 함");

      // processPage는 fetchTotalPage 이후에 호출
      assertTrue(callOrder.indexOf("processPage-1") > 0, "processPage는 fetchTotalPage 이후에 호출되어야 함");
    }

    @Test
    @DisplayName("fetchTotalPage는 정확히 1번만 호출됨")
    void fetchTotalPageCalledExactlyOnce() {
      AtomicInteger fetchTotalPageCallCount = new AtomicInteger(0);

      AbstractPageProcessor<IntPageCriteria> processor = new AbstractPageProcessor<>() {
        @Override
        protected int fetchTotalPage(IntPageCriteria criteria) {
          fetchTotalPageCallCount.incrementAndGet();
          return 10;
        }

        @Override
        protected PageInfo processPage(IntPageCriteria criteria) {
          return new PageInfo(10, 100, 10);
        }

        @Override
        protected void saveBatch() {
        }
      };
      processor.setBaseCriteria(new IntPageCriteria(1));

      processor.process(3);

      assertEquals(1, fetchTotalPageCallCount.get(), "fetchTotalPage는 정확히 1번만 호출되어야 함");
    }

    @Test
    @DisplayName("fetchTotalPage 실패 시 processPage는 호출되지 않음")
    void fetchTotalPageFailure_processPageNotCalled() {
      AtomicBoolean processPageCalled = new AtomicBoolean(false);

      AbstractPageProcessor<IntPageCriteria> processor = new AbstractPageProcessor<>() {
        @Override
        protected int fetchTotalPage(IntPageCriteria criteria) {
          throw new RuntimeException("fetchTotalPage 실패");
        }

        @Override
        protected PageInfo processPage(IntPageCriteria criteria) {
          processPageCalled.set(true);
          return new PageInfo(10, 100, 10);
        }

        @Override
        protected void saveBatch() {
        }
      };
      processor.setBaseCriteria(new IntPageCriteria(1));

      assertThrows(RuntimeException.class, () -> processor.process(5));

      assertTrue(!processPageCalled.get(), "fetchTotalPage 실패 시 processPage는 호출되면 안됨");
    }

    @Test
    @DisplayName("fetchTotalPage와 processPage는 독립적인 API 호출이 가능")
    void fetchTotalPageAndProcessPage_independentCalls() {
      // 시나리오: fetchTotalPage는 /api/count를, processPage는 /api/articles를 호출
      List<String> apiCalls = Collections.synchronizedList(new ArrayList<>());

      AbstractPageProcessor<IntPageCriteria> processor = new AbstractPageProcessor<>() {
        @Override
        protected int fetchTotalPage(IntPageCriteria criteria) {
          apiCalls.add("GET /api/count");
          return 3;
        }

        @Override
        protected PageInfo processPage(IntPageCriteria criteria) {
          apiCalls.add("GET /api/articles?page=" + criteria.page());
          return new PageInfo(3, 30, 10);
        }

        @Override
        protected void saveBatch() {
        }
      };
      processor.setBaseCriteria(new IntPageCriteria(1));

      processor.process(10);

      // fetchTotalPage는 1번, processPage는 3번
      assertEquals(1, apiCalls.stream().filter(c -> c.contains("/api/count")).count());
      assertEquals(3, apiCalls.stream().filter(c -> c.contains("/api/articles")).count());

      // 호출 순서: count -> articles
      assertEquals("GET /api/count", apiCalls.get(0));
    }
  }

  @Nested
  @DisplayName("페이지 순회 로직 검증")
  class PageIterationTests {

    @Test
    @DisplayName("fetchTotalPage 반환값만큼 페이지 처리")
    void processesExactNumberOfPages() {
      AtomicInteger processedPageCount = new AtomicInteger(0);
      List<Integer> processedPages = Collections.synchronizedList(new ArrayList<>());

      AbstractPageProcessor<IntPageCriteria> processor = new AbstractPageProcessor<>() {
        @Override
        protected int fetchTotalPage(IntPageCriteria criteria) {
          return 7; // 7페이지
        }

        @Override
        protected PageInfo processPage(IntPageCriteria criteria) {
          processedPageCount.incrementAndGet();
          processedPages.add(criteria.page());
          return new PageInfo(7, 70, 10);
        }

        @Override
        protected void saveBatch() {
        }
      };
      processor.setBaseCriteria(new IntPageCriteria(1));

      ItemProcessedResult result = processor.process(3);

      assertEquals(7, processedPageCount.get(), "7페이지 모두 처리되어야 함");
      assertEquals(7, result.successCount());
      assertTrue(processedPages.contains(1));
      assertTrue(processedPages.contains(7));
    }

    @Test
    @DisplayName("fetchTotalPage가 0이면 processPage 호출 없음")
    void zeroTotalPage_noProcessing() {
      AtomicBoolean processPageCalled = new AtomicBoolean(false);

      AbstractPageProcessor<IntPageCriteria> processor = new AbstractPageProcessor<>() {
        @Override
        protected int fetchTotalPage(IntPageCriteria criteria) {
          return 0;
        }

        @Override
        protected PageInfo processPage(IntPageCriteria criteria) {
          processPageCalled.set(true);
          return new PageInfo(0, 0, 0);
        }

        @Override
        protected void saveBatch() {
        }
      };
      processor.setBaseCriteria(new IntPageCriteria(1));

      ItemProcessedResult result = processor.process(5);

      assertTrue(!processPageCalled.get(), "totalPage가 0이면 processPage 호출 안됨");
      assertEquals(0, result.totalProcessed());
    }

    @Test
    @DisplayName("배치 크기보다 페이지가 적을 때")
    void fewerPagesThanBatchSize() {
      List<Integer> processedPages = Collections.synchronizedList(new ArrayList<>());

      AbstractPageProcessor<IntPageCriteria> processor = new AbstractPageProcessor<>() {
        @Override
        protected int fetchTotalPage(IntPageCriteria criteria) {
          return 2; // 2페이지만
        }

        @Override
        protected PageInfo processPage(IntPageCriteria criteria) {
          processedPages.add(criteria.page());
          return new PageInfo(2, 20, 10);
        }

        @Override
        protected void saveBatch() {
        }
      };
      processor.setBaseCriteria(new IntPageCriteria(1));

      ItemProcessedResult result = processor.process(10); // 배치 크기 10

      assertEquals(2, result.successCount());
      assertEquals(2, processedPages.size());
    }
  }

  @Nested
  @DisplayName("baseCriteria 검증")
  class BaseCriteriaTests {

    @Test
    @DisplayName("baseCriteria 미설정 시 예외 발생")
    void baseCriteriaNotSet_throwsException() {
      AbstractPageProcessor<IntPageCriteria> processor = new AbstractPageProcessor<>() {
        @Override
        protected int fetchTotalPage(IntPageCriteria criteria) {
          return 5;
        }

        @Override
        protected PageInfo processPage(IntPageCriteria criteria) {
          return new PageInfo(5, 50, 10);
        }

        @Override
        protected void saveBatch() {
        }
      };

      assertThrows(IllegalStateException.class, () -> processor.process(5),
          "baseCriteria 미설정 시 IllegalStateException");
    }

    @Test
    @DisplayName("baseCriteria의 페이지 번호가 processPage에 올바르게 전달됨")
    void baseCriteria_pageNumberCorrectlyPassed() {
      List<Integer> pageNumbers = Collections.synchronizedList(new ArrayList<>());

      AbstractPageProcessor<IntPageCriteria> processor = new AbstractPageProcessor<>() {
        @Override
        protected int fetchTotalPage(IntPageCriteria criteria) {
          return 3;
        }

        @Override
        protected PageInfo processPage(IntPageCriteria criteria) {
          pageNumbers.add(criteria.page());
          return new PageInfo(3, 30, 10);
        }

        @Override
        protected void saveBatch() {
        }
      };
      processor.setBaseCriteria(new IntPageCriteria(1));

      processor.process(10);

      // 페이지 1, 2, 3이 순서대로 처리되어야 함
      assertTrue(pageNumbers.contains(1));
      assertTrue(pageNumbers.contains(2));
      assertTrue(pageNumbers.contains(3));
      assertEquals(3, pageNumbers.size());
    }
  }

  @Nested
  @DisplayName("getTotalCount 동작 검증")
  class GetTotalCountTests {

    @Test
    @DisplayName("getTotalCount는 fetchTotalPage 반환값을 반환")
    void getTotalCountReturnsPageCount() {
      AtomicInteger capturedTotalCount = new AtomicInteger(-1);

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
        }
      };
      processor.setBaseCriteria(new IntPageCriteria(1));

      processor.process(5, new IItemProcessorLogger() {
        @Override
        public void onStart(Long totalCount) {
          if (totalCount != null) {
            capturedTotalCount.set(totalCount.intValue());
          }
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

      assertEquals(15, capturedTotalCount.get(), "getTotalCount는 fetchTotalPage 반환값을 반환해야 함");
    }
  }

  @Nested
  @DisplayName("processPage 실패 처리")
  class ProcessPageFailureTests {

    @Test
    @DisplayName("processPage 일부 실패 시 실패 카운트 정확히 기록")
    void processPageFailure_countedCorrectly() {
      AbstractPageProcessor<IntPageCriteria> processor = new AbstractPageProcessor<>() {
        @Override
        protected int fetchTotalPage(IntPageCriteria criteria) {
          return 5;
        }

        @Override
        protected PageInfo processPage(IntPageCriteria criteria) {
          if (criteria.page() == 3) {
            throw new RuntimeException("페이지 3 처리 실패");
          }
          return new PageInfo(5, 50, 10);
        }

        @Override
        protected void saveBatch() {
        }
      };
      processor.setBaseCriteria(new IntPageCriteria(1));

      ItemProcessedResult result = processor.process(10);

      assertEquals(5, result.totalProcessed());
      assertEquals(4, result.successCount());
      assertEquals(1, result.failureCount());
    }
  }
}
