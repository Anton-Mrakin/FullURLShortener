# FlipURLShortener

A high-performance, scalable URL shortener service built with Spring Boot 3, following Clean Architecture principles.

## Features

- **Flexible Code Generation**: Supports multiple strategies for short code generation:
  - **SHA-256**: Deterministic (same URL always gives same code), URL-safe Base64 encoded.
  - **Random String**: Purely random alphanumeric strings.
  - **Base62**: Encodes random 64-bit integers into Base62 strings.
- **Retrieve URL**: Retrieves the original URL using the short code and updates `lastAccessed` timestamp.
- **Clean Architecture**: Strict separation between Domain, Use Cases, and Infrastructure layers.
- **High Performance**: Optimized for high concurrency with parallel processing, optimistic locking, and metrics.
- **Resilience**:
  - **Rate Limiting**: Protects the shortening endpoint using Resilience4j.
  - **Optimistic Locking**: Ensures data consistency with automatic exponential backoff retries (Resilience4j) on conflicts (ObjectOptimisticLockingFailureException).
  - **Data Integrity**: Handles concurrent creation of the same URL or code collisions via Retry policies. If a duplicate `original_url` or `short_code` is detected during save, the operation is retried.
- **Transaction & Isolation**:
  - **Global Isolation Level**: `TRANSACTION_READ_COMMITTED` (configured via HikariCP).
  - **Optimized Flow**: Checking for existing URLs (`findByOriginalUrl`) is performed outside of transactions. Transactions are only used for the actual saving of new records, reducing lock duration.
- **Storage Management**:
  - Configurable URL limit (`app.url-limit`).
  - **Asynchronous LRU Eviction**: When the limit is reached, an asynchronous task (`UrlLimitAspect`) handles the deletion of records exceeding the limit (based on `lastAccessed`).
  - **Periodic Maintenance**: A scheduled task (`UrlMaintenanceService`) runs every minute (`PT1M`) as a safeguard to ensure the storage limit is maintained, even if asynchronous cleanup fails. Both tasks have a 60-second timeout to prevent hanging.
- **Input Validation**:
  - **NotEmpty**: Ensures the URL is not null or empty.
  - **MaxLength**: Enforces a configurable maximum URL length (`app.max-url-length`).
  - **Extensible Architecture**: Easy to add new validators by implementing the `UrlValidator` interface and making them Spring Beans.
- **Cloud Native**:
  - Kubernetes-ready with Liveness and Readiness probes.
  - Prometheus metrics for monitoring:
    - `url_count`: Total stored URLs.
    - `url_shorten_requests_total`: Counter for shorten requests.
    - `url_get_requests_total`: Counter for retrieval requests.
- **Observability**:
  - **Loki Integration**: Logs are sent to Grafana Loki for centralized log management.
  - **Tracing**: Distributed tracing support with `X-Request-ID` header and MDC logging.
- **Database**: PostgreSQL with Flyway for versioned migrations.

## Tech Stack

- **Java 21** (GraalVM compatible)
- **Spring Boot 3.3.4**
- **Data JPA / Hibernate**
- **PostgreSQL**
- **FlywayDB**
- **Lombok**
- **MapStruct**
- **Resilience4j** (Retry, RateLimiter)
- **Micrometer / Prometheus**
- **Loki Logback Appender**
- **Testcontainers** (for integration testing)
- **JMH** (for micro-benchmarking)

## Architecture

The project is structured according to **Clean Architecture**:

- `com.mrakin.domain`: Domain models (`Url`), exceptions, and repository ports. Zero external dependencies.
- `com.mrakin.usecases`: Business logic (`ShortenUrlUseCase`, `GetOriginalUrlUseCase`) and code generators.
- `com.mrakin.infra`: Implementation details:
  - `db`: Persistence (JPA, Flyway migrations, entities).
  - `rest`: API layer (Controllers, Filters, Exception Handling).

## Database Schema

The `urls` table structure:
- `id` (BIGINT): Primary Key, auto-generated identity.
- `short_code` (VARCHAR): Unique short identifier.
- `original_url` (TEXT): Unique original URL.
- `created_at` (TIMESTAMP): Creation time.
- `last_accessed` (TIMESTAMP): Last time the URL was retrieved or shortened.
- `version` (BIGINT): Version field for optimistic locking.

## API Endpoints

### Shorten URL
- **URL**: `POST /api/v1/urls/shorten`
- **Content-Type**: `text/plain`
- **Body**: The long URL string.
- **Response**: `200 OK` with the short code string.

### Retrieve URL
- **URL**: `GET /api/v1/urls/{shortCode}`
- **Response**: `200 OK` with the original URL string.

## Development & Testing

### Running Tests
- **Unit Tests**: `UrlUseCaseTest`, `ShortCodeGeneratorTest`.
- **Integration Tests**: `UrlShortenerIntegrationTest` (PostgreSQL via Testcontainers).
- **Performance Tests**: Integrated into `UrlShortenerIntegrationTest` with assertions on latency and throughput.
- **Benchmarks**: JMH tests in `src/main/java/com/mrakin/benchmark/` for generator performance comparison.

### Building & Running
```bash
# Build the project
./mvnw clean package

# Run benchmarks
java -cp "target/classes:$(mvn dependency:build-classpath | grep -v '[INFO]')" org.openjdk.jmh.Main ShortCodeGeneratorBenchmark
```
Current results:
Benchmark                                      Mode  Cnt          Score           Error  Units
ShortCodeGeneratorBenchmark.testBase62        thrpt    3   41602031,095 ± 120176364,039  ops/s
ShortCodeGeneratorBenchmark.testRandomString  thrpt    3  113087667,077 ±  56660206,818  ops/s
ShortCodeGeneratorBenchmark.testSha256        thrpt    3   10464639,705 ±  30578843,951  ops/s

## Configuration

Key properties in `application.yml`:
- `app.url-limit`: Storage capacity.
- `app.max-url-length`: Maximum length of the input URL (default 2048).
- `app.generator.name`: Selected generator (`sha256Generator`, `randomStringGenerator`, `base62Generator`).
- `app.short-code-length`: Length of codes.
- `logging.loki.url`: URL for Grafana Loki.
