# Web Collector Base

웹 수집 프로젝트를 위한 베이스 라이브러리

## 설치

### Gradle (JitPack)

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.agent-hanju:web-collector-base:0.3.0'
}
```

## 사용법

### 아이템 프로세서 (AbstractItemProcessor)

DB 커서, JPA Scroll API, Iterator 기반의 스트림/배치 처리에 사용합니다.
메모리 효율적으로 대용량 데이터를 처리할 수 있습니다.

```java
@Component
public class ArticleProcessor extends AbstractItemProcessor<Long> {

    private final ArticleRepository repository;
    private final SearchIndexService indexService;
    private final List<Article> buffer = new ArrayList<>();
    private int offset = 0;

    @Override
    protected Long getTotalCount() {
        return repository.count();  // 선택적: 진행률 표시용
    }

    @Override
    protected List<Long> fetchNextBatch(int batchSize) {
        List<Long> ids = repository.findIds(offset, batchSize);
        offset += ids.size();
        return ids;  // 빈 리스트 반환 시 종료
    }

    @Override
    protected void processItem(Long articleId) {
        Article article = repository.findById(articleId).orElseThrow();
        indexService.index(article);
        buffer.add(article);
    }

    @Override
    protected void saveBatch() {
        indexService.flush();
        buffer.clear();
    }
}

// 사용
ItemProcessedResult result = processor.process(100); // 100건씩 배치 처리
```

### 페이지 프로세서 (AbstractPageProcessor)

페이지 기반 API 수집에 사용합니다. `AbstractItemProcessor`를 상속하며, 페이지 순회 로직이 내부에 구현되어 있어 `fetchTotalPage()`, `processPage()`, `saveBatch()`를 구현하면 됩니다.

#### 내부 동작

`AbstractPageProcessor`는 `AbstractItemProcessor`의 메서드를 다음과 같이 구현합니다:

- **`process(batchSize)`**: 페이지 처리 전에 `fetchTotalPage()`를 호출하여 전체 페이지 수를 미리 파악한 뒤, `super.process()`를 호출합니다.

- **`fetchNextBatch(batchSize)`**: `setBaseCriteria()`로 설정한 검색 조건을 기반으로, 다음 `batchSize`개 페이지에 대한 검색 조건(`PageCriteria`) 목록을 생성합니다. 마지막 페이지를 초과하면 빈 리스트를 반환하여 종료합니다.

- **`processItem(criteria)`**: 전달받은 검색 조건으로 `processPage()`를 호출합니다.

따라서 사용자는 **페이지 번호 관리 없이** `fetchTotalPage()`에서 전체 페이지 수 조회, `processPage()`에서 API 호출/파싱 로직만 작성하면 됩니다.

#### 검색 조건 정의 (PageCriteria)

```java
// 검색 조건 클래스 정의
public class ArticleSearchCriteria implements PageCriteria<ArticleSearchCriteria> {
    private final String keyword;
    private final LocalDate startDate;
    private final int page;

    public ArticleSearchCriteria(String keyword, LocalDate startDate, int page) {
        this.keyword = keyword;
        this.startDate = startDate;
        this.page = page;
    }

    @Override
    public ArticleSearchCriteria ofPage(int page) {
        return new ArticleSearchCriteria(keyword, startDate, page);
    }

    public String toUrl() {
        return "/api/articles?keyword=" + keyword + "&startDate=" + startDate + "&page=" + page;
    }

    // getters...
}
```

#### 프로세서 구현

```java
@Component
public class ArticlePageProcessor extends AbstractPageProcessor<ArticleSearchCriteria> {

    private final ArticleApiClient apiClient;
    private final ArticleRepository repository;
    private final List<Article> buffer = new ArrayList<>();

    @Override
    protected int fetchTotalPage(ArticleSearchCriteria criteria) {
        // 전체 페이지 수만 조회 (페이지 처리와 분리)
        return apiClient.getTotalPage(criteria.toUrl());
    }

    @Override
    protected PageInfo processPage(ArticleSearchCriteria criteria) {
        ApiResponse response = apiClient.get(criteria.toUrl());
        List<Article> articles = parseArticles(response);
        buffer.addAll(articles);
        return new PageInfo(response.getTotalPage(), response.getTotalCount(), articles.size());
    }

    @Override
    protected void saveBatch() {
        repository.saveAll(buffer);
        buffer.clear();
    }
}

// 사용
processor.setBaseCriteria(new ArticleSearchCriteria("java", LocalDate.of(2024, 1, 1), 0));
ItemProcessedResult result = processor.process(10); // 10페이지마다 저장
```

### 병렬 처리 설정

`BatchExecutionConfig`를 오버라이드하여 병렬 처리를 설정합니다.

```java
@Component
public class ArticleProcessor extends AbstractItemProcessor<Long> {

    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean shutdownRequested = false;

    @Override
    public Executor getExecutor() {
        return executor; // 수집 작업 병렬 실행
    }

    @Override
    public int getMaxPendingFlushes() {
        return 3; // 동시에 진행 가능한 flush 작업 수 (기본값: 3)
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

| 메서드                  | 용도                           | 기본값                 |
| ----------------------- | ------------------------------ | ---------------------- |
| `getExecutor()`         | 배치 내 작업 병렬 실행         | `Runnable::run` (동기) |
| `getMaxPendingFlushes()`| 동시 진행 가능한 flush 수      | `3`                    |

### 로깅

`IItemProcessorLogger`를 구현하여 처리 진행 상황을 로깅할 수 있습니다.

```java
ItemProcessedResult result = processor.process(100, new IItemProcessorLogger() {
    @Override
    public void onStart(Long totalCount) {
        log.info("처리 시작: 총 {}건", totalCount);
    }

    @Override
    public void onBatchFetched(Integer batch, Integer itemCount) {
        log.debug("배치 {} 로드: {}건", batch, itemCount);
    }

    @Override
    public void onComplete(Long totalProcessed, Long successCount, Long failureCount) {
        log.info("처리 완료: 성공 {}건, 실패 {}건", successCount, failureCount);
    }

    // ... 나머지 메서드
});
```

## 주요 컴포넌트

### Core

| 클래스                     | 설명                                        |
| -------------------------- | ------------------------------------------- |
| `BatchExecutionConfig`     | Executor, 종료 요청 설정을 위한 인터페이스  |
| `AbstractItemProcessor<T>` | 스트림/커서 기반 배치 처리를 위한 추상 클래스 |
| `AbstractPageProcessor<C>` | 페이지 기반 수집을 위한 추상 클래스 (extends AbstractItemProcessor) |
| `PageCriteria`             | 페이지 검색 조건 마커 인터페이스            |
| `IItemProcessorLogger`     | 아이템 처리 진행 로깅 인터페이스            |

### Core DTO

| 클래스                | 설명                                                      |
| --------------------- | --------------------------------------------------------- |
| `PageInfo`            | 페이지 정보 (전체 페이지, 전체 아이템 수, 현재 아이템 수) |
| `ItemProcessedResult` | 처리 결과 (전체, 성공, 실패 건수)                         |

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
