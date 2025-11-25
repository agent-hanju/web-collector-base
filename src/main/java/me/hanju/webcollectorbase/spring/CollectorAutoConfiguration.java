package me.hanju.webcollectorbase.spring;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import me.hanju.webcollectorbase.core.BatchExecutionConfig;

/**
 * Spring Boot AutoConfiguration for web-collector-base.
 * BatchExecutionConfig가 클래스패스에 있을 때 자동으로 ShutdownHookManager를 등록합니다.
 */
@AutoConfiguration
@ConditionalOnClass(BatchExecutionConfig.class)
public class CollectorAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ShutdownHookManager shutdownHookManager(List<BatchExecutionConfig> collectors) {
    return new ShutdownHookManager(collectors);
  }
}
