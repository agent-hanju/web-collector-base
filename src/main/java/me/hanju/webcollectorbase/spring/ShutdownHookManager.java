package me.hanju.webcollectorbase.spring;

import java.util.List;

import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.hanju.webcollectorbase.core.BatchExecutionConfig;

/**
 * Spring 컨텍스트 종료 시 모든 Collector에 shutdown을 요청하는 매니저.
 */
@Slf4j
@RequiredArgsConstructor
public class ShutdownHookManager {

  private final List<BatchExecutionConfig> collectors;

  @EventListener(ContextClosedEvent.class)
  public void onContextClosed() {
    log.info("Requesting shutdown for {} collectors", collectors.size());
    collectors.forEach(BatchExecutionConfig::requestShutdown);
  }
}
