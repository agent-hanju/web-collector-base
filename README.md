# Web Collector Base

웹 수집 프로젝트를 위한 베이스 라이브러리

## 설치

### Gradle (JitPack)

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.agent-hanju:web-collector-base:0.2.0'
}
```

## 사용법

### 목록 수집기 (AbstractListCollector)

페이지 기반 목록 수집에 사용합니다.

```java
@Component
public class ArticleListCollector extends AbstractListCollector {

    private final ArticleApiClient apiClient;
    private final ArticleRepository repository;
    private final List<Article> buffer = new ArrayList<>();

    @Override
    protected PageInfo processPage(int page) {
        ApiResponse response = apiClient.getList(page);
        List<Article> articles = parseArticles(response);
        buffer.addAll(articles);
        return new PageInfo(response.getTotalPage(), response.getTotalCount(), articles.size());
    }

    @Override
    protected void saveBatch() {
        // withSemaphore로 DB 동시 접근 제어
        withSemaphore(() -> {
            repository.saveAll(buffer);
            buffer.clear();
        });
    }
}

// 사용
ListCollectedResult result = collector.collect(10); // 10페이지마다 저장
```

### 본문 수집기 (AbstractContentCollector)

ID 기반 상세 본문 수집에 사용합니다.

```java
@Component
public class ArticleContentCollector extends AbstractContentCollector<Long> {

    private final ArticleApiClient apiClient;
    private final ArticleRepository repository;
    private final List<ArticleContent> buffer = new ArrayList<>();

    @Override
    protected void processContent(Long articleId) {
        ApiResponse response = apiClient.getContent(articleId);
        ArticleContent content = parseContent(response);
        buffer.add(content);
    }

    @Override
    protected void saveBatch() {
        // withSemaphore로 DB 동시 접근 제어
        withSemaphore(() -> {
            repository.saveAll(buffer);
        });
        buffer.clear();
    }
}

// 사용
List<Long> ids = repository.findAllIds();
ContentCollectedResult result = collector.collect(ids, 50); // 50건마다 저장
```

### 병렬 처리 설정

`BatchExecutionConfig`를 오버라이드하여 병렬 처리를 설정합니다.

```java
@Component
public class ArticleListCollector extends AbstractListCollector {

    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Executor flushExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean shutdownRequested = false;

    @Override
    public Executor getExecutor() {
        return executor; // 수집 작업 병렬 실행
    }

    @Override
    public Executor getFlushExecutor() {
        return flushExecutor; // 저장 작업 비동기 실행 (v0.2.0)
    }

    @Override
    public boolean isShutdownRequested() {
        return shutdownRequested;
    }

    @Override
    public void requestShutdown() {
        shutdownRequested = true;
    }
}
```

| 메서드               | 용도                          | 기본값                 |
| -------------------- | ----------------------------- | ---------------------- |
| `getExecutor()`      | 배치 내 작업 병렬 실행        | `Runnable::run` (동기) |
| `getFlushExecutor()` | 배치 완료 후 저장 비동기 실행 | `Runnable::run` (동기) |

**비동기 플러시 동작 (v0.2.0):**

- 배치 수집 완료 → 비동기로 저장 시작 → 다음 배치 수집 시작
- `collect()` 반환 전 모든 저장 완료 보장
- 저장 실패해도 수집은 계속 진행

### DB 동시 접근 제어

`getSemaphore()`를 오버라이드하여 DB 동시 접근을 제한합니다.

```java
@Component
public class ArticleListCollector extends AbstractListCollector {

    private final Semaphore semaphore = new Semaphore(1); // DB 동시 접근 1개로 제한

    @Override
    public Semaphore getSemaphore() {
        return semaphore;
    }

    @Override
    protected void saveBatch() {
        withSemaphore(() -> {
            repository.saveAll(buffer);
        });
        buffer.clear();
    }
}
```

### 로깅

`ICollectorLogger`를 구현하여 수집 진행 상황을 로깅할 수 있습니다.

```java
ListCollectedResult result = collector.collect(10, new ICollectorLogger() {
    @Override
    public void onStart(Integer totalUnit, Integer totalItem) {
        log.info("수집 시작: {}페이지, {}건", totalUnit, totalItem);
    }

    @Override
    public void onUnitSuccess(Integer unit, Integer itemCount) {
        log.debug("페이지 {} 완료: {}건", unit, itemCount);
    }

    @Override
    public void onComplete(Integer totalUnit, Integer totalItem,
                          Integer failureCount, Integer successItemCount) {
        log.info("수집 완료: 성공 {}건, 실패 {}건", successItemCount, failureCount);
    }

    // ... 나머지 메서드
});
```

## 주요 컴포넌트

### Core

| 클래스                        | 설명                                       |
| ----------------------------- | ------------------------------------------ |
| `BatchExecutionConfig`        | Executor, 종료 요청 설정을 위한 인터페이스 |
| `AbstractListCollector`       | 페이지 기반 목록 수집을 위한 추상 클래스   |
| `AbstractContentCollector<T>` | ID 기반 본문 수집을 위한 추상 클래스       |
| `ICollectorLogger`            | 수집 진행 로깅 인터페이스                  |

### Core DTO

| 클래스                   | 설명                                                      |
| ------------------------ | --------------------------------------------------------- |
| `PageInfo`               | 페이지 정보 (전체 페이지, 전체 아이템 수, 현재 아이템 수) |
| `ListCollectedResult`    | 목록 수집 결과 (수집 건수, 신규 건수 등)                  |
| `ContentCollectedResult` | 본문 수집 결과 (전체, 성공, 실패 건수)                    |

### JPA

이 프로젝트에서 사용되지 않습니다.(Deprecated 예정)

| 클래스                   | 설명                                               |
| ------------------------ | -------------------------------------------------- |
| `AbstractDomainEntity`   | unexpectedFieldMap을 제공하는 도메인 엔티티 베이스 |
| `AbstractResponseEntity` | API 응답 rawData 저장용 엔티티 베이스              |
| `StringMapConverter`     | `Map<String, String>` ↔ JSON 변환기                |

### Spring (Optional)

| 클래스                       | 설명                           |
| ---------------------------- | ------------------------------ |
| `ShutdownHookManager`        | 종료 시 수집기에 shutdown 요청 |
| `CollectorAutoConfiguration` | Spring Boot AutoConfiguration  |

## 요구사항

- Java 17+
- Spring Boot 3.x (optional)

## 라이선스

MIT License
