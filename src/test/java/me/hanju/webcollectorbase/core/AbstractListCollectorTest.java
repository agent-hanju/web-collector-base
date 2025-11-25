package me.hanju.webcollectorbase.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

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
      protected void saveBatch() {}

      @Override
      public boolean isShutdownRequested() {
        return shutdown;
      }
    };

    collector.collect(10);

    // 첫 페이지 + 2페이지까지 처리 후 중단 (3페이지부터 중단)
    assertEquals(2, collected.size());
  }
}
