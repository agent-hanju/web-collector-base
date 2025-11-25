package me.hanju.webcollectorbase.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

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
      protected void processContent(Long id) {}

      @Override
      protected void saveBatch() {}
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
      protected void processContent(Long id) {}

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
}
