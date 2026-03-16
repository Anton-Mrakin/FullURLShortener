# FlipURLShortener

A high-performance, scalable URL shortener service built with Spring Boot 3, following Clean Architecture principles.

## Features

- **Shorten URL**: Converts long URLs into short, deterministic codes using SHA-256.
- **Retrieve URL**: Retrieves the original URL using the short code.
- **Clean Architecture**: Strict separation between Domain, Use Cases, and Infrastructure layers.
- **High Performance**: Optimized for high concurrency with parallel processing and metrics.
- **Resilience**:
  - **Rate Limiting**: Protects the shortening endpoint using Resilience4j.
  - **Optimistic Locking**: Ensures data consistency with automatic retries on conflicts.
- **Storage Management**:
  - Configurable URL limit.
  - Automatic LRU (Least Recently Used) eviction policy when the limit is reached.
- **Cloud Native**:
  - Kubernetes-ready with Liveness and Readiness probes.
  - Prometheus metrics for monitoring (request counts, storage usage).
  - Distributed tracing support with `X-Request-ID` header and MDC logging.
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
- **Testcontainers** (for integration testing)

## Architecture

The project is structured according to **Clean Architecture**:

- `com.mrakin.domain`: Contains domain models (`Url`), exceptions, and repository ports (interfaces). It has no dependencies on external frameworks.
- `com.mrakin.usecases`: Implements business logic (`ShortenUrlUseCase`, `GetOriginalUrlUseCase`). It depends only on the domain layer.
- `com.mrakin.infra`: Implementation details:
  - `db`: Persistence layer (JPA repositories, entities, mappers).
  - `rest`: API layer (Controllers, filters, global exception handler).
  - `config`: Spring configuration.

## API Endpoints

### Shorten URL
- **URL**: `POST /api/v1/urls/shorten`
- **Content-Type**: `text/plain`
- **Body**: The long URL to shorten.
- **Response**: `200 OK` with the short code.

### Retrieve URL
- **URL**: `GET /api/v1/urls/{shortCode}`
- **Response**: `200 OK` with the original URL.

## Monitoring

- **Health Probes**: `/actuator/health/liveness` and `/actuator/health/readiness`
- **Metrics**: `/actuator/prometheus`
  - `url_count`: Gauge of total stored URLs.
  - `url_shorten_requests_total`: Counter for shorten requests.
  - `url_get_requests_total`: Counter for retrieval requests.

## Development & Testing

### Running Tests
The project includes a comprehensive test suite:
- **Unit Tests**: `UrlUseCaseTest` covers core business logic and edge cases using `@ParameterizedTest`.
- **Integration Tests**: `UrlShortenerIntegrationTest` uses Testcontainers (PostgreSQL) to verify:
  - End-to-end REST API functionality.
  - Rate limiting behavior.
  - High-concurrency performance (latency and throughput).
  - Startup time thresholds.
  - Prometheus metrics and Kubernetes probes.

### Building & Running
```bash
# Build the project
./mvnw clean package

# Run with Docker
docker build -t flip-url-shortener .
docker run -p 8080:8080 flip-url-shortener
```

## Configuration

Key application properties can be found in `src/main/resources/application.yml`:
- `app.url-limit`: Maximum number of URLs to store.
- `app.short-code-length`: Length of the generated short codes.
- `resilience4j.ratelimiter`: Rate limiter settings.
- `resilience4j.retry`: Retry policy for optimistic locking.
