# Tenpo Backend

Spring Boot 3 REST API for the Tenpo Full Stack Challenge.
Exposes a clean HTTP contract for transaction CRUD, enforces business rules,
persists data in PostgreSQL, documents the API with Swagger, and protects the
service with structured error handling and per-client rate limiting.

---

## Table of Contents

1. [Stack](#1-stack)
2. [Package Layout and Class Relationships](#2-package-layout-and-class-relationships)
3. [Request Flow Diagrams](#3-request-flow-diagrams)
4. [Domain Rules](#4-domain-rules)
5. [Data Model](#5-data-model)
6. [API Summary](#6-api-summary)
7. [Error Model](#7-error-model)
8. [Rate Limiting](#8-rate-limiting)
9. [SOLID Analysis](#9-solid-analysis)
10. [Security Notes](#10-security-notes)
11. [Performance Notes](#11-performance-notes)
12. [Configuration](#12-configuration)
13. [Local Development](#13-local-development)
14. [Docker](#14-docker)
15. [Testing](#15-testing)
16. [Manual Verification](#16-manual-verification)
17. [Docker Hub Publication](#17-docker-hub-publication)

---

## 1. Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 17 | Language / runtime |
| Spring Boot | 3.x | Framework, auto-configuration |
| Spring Web | — | DispatcherServlet, `@RestController` |
| Spring Validation | — | Bean Validation, `@Valid` |
| Spring Data JPA + Hibernate | — | ORM, derived queries |
| PostgreSQL | 16 | Primary database |
| Flyway | — | Versioned schema migrations |
| springdoc-openapi | — | Swagger UI + OpenAPI JSON |
| Caffeine | — | In-memory rate-limit counter cache |
| JUnit 5 + Spring Boot Test | — | Unit and integration tests |
| H2 (test profile) | — | In-memory DB for tests (PostgreSQL mode) |

---

## 2. Package Layout and Class Relationships

### Package structure

```
src/main/java/com/tenpo/challenge/
│
├── TenpobackApplication.java              ← @SpringBootApplication entry point
│
├── transaction/                           ← Domain aggregate (all CRUD logic)
│   ├── Transaction.java                   ← @Entity: columns, @PrePersist/@PreUpdate
│   ├── TransactionRequest.java            ← Input DTO: Bean Validation annotations
│   ├── TransactionResponse.java           ← Output DTO: excludes internal fields
│   ├── TransactionMapper.java             ← Entity ↔ DTO (SRP: mapping only)
│   ├── TransactionRepository.java         ← Spring Data JPA interface (derived queries)
│   ├── TransactionService.java            ← Business logic (quota, sanitize, convert)
│   └── TransactionController.java         ← REST endpoints (HTTP concerns only)
│
├── shared/
│   ├── api/
│   │   ├── ApiError.java                  ← Immutable error response record
│   │   ├── ApiErrorFactory.java           ← Builds ApiError with timestamp + reason
│   │   └── ApiFieldError.java             ← Per-field validation error record
│   └── exception/
│       ├── BusinessRuleException.java     ← Domain exception carrying HttpStatus
│       ├── ResourceNotFoundException.java ← Always maps to HTTP 404
│       └── GlobalExceptionHandler.java    ← @RestControllerAdvice: 6 handlers
│
├── rate/
│   ├── ClientRateLimiter.java             ← Fixed-window algorithm (Caffeine-backed)
│   ├── RateLimitFilter.java               ← OncePerRequestFilter: applies limit to /api/**
│   ├── RateLimitDecision.java             ← Value record: allowed/blocked + retryAfter
│   ├── ClientKeyResolver.java             ← Extracts client IP (X-Forwarded-For)
│   └── RateLimitProperties.java           ← @ConfigurationProperties: capacity, duration
│
└── config/
    ├── WebConfiguration.java              ← CORS allowlist (fail-closed if empty)
    ├── CorsProperties.java                ← @ConfigurationProperties: allowed-origins
    └── OpenApiConfiguration.java          ← OpenAPI metadata + Swagger UI config
```

### Class dependency graph

```
TenpobackApplication
    │ @SpringBootApplication + @EnableConfigurationProperties
    ▼
┌─────────────────────────────────────────────────────────────────────┐
│  HTTP Pipeline                                                       │
│  RateLimitFilter ──▶ ClientKeyResolver (resolve IP)                 │
│       │              ClientRateLimiter (check/decrement counter)     │
│       │                 └── Caffeine Cache (FixedWindowCounter)      │
│       │ (blocked) ──▶ 429 JSON response                              │
│       │ (allowed) ──▶ DispatcherServlet                              │
└─────────────────────────────────────────────────────────────────────┘
    ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Controller Layer                                                    │
│  TransactionController (@RestController)                             │
│    POST /api/transactions       → create()                           │
│    GET  /api/transactions       → list()                             │
│    GET  /api/transactions/{id}  → get()                              │
│    PUT  /api/transactions/{id}  → update()                           │
│    DELETE /api/transactions/{id}→ delete()                           │
│                                                                      │
│  GlobalExceptionHandler (@RestControllerAdvice)                      │
│    catches exceptions from any @RestController method                │
└─────────────────────────────────────────────────────────────────────┘
    ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Service Layer                                                       │
│  TransactionService                                                  │
│    create()  → quota check → sanitize → save                        │
│    update()  → find → sanitize → save                               │
│    delete()  → find → delete                                         │
│    applyRequest() [private] ← shared by create + update             │
└─────────────────────────────────────────────────────────────────────┘
    ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Persistence Layer                                                   │
│  TransactionRepository (JpaRepository<Transaction, Integer>)        │
│    findAll(Sort)                                                     │
│    findByCustomerNameNormalized(name, pageable)                      │
│    countByCustomerNameNormalized(name) ← used for quota check       │
└─────────────────────────────────────────────────────────────────────┘
    ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Database                                                            │
│  PostgreSQL 16 — schema managed by Flyway                            │
│  Table: transactions                                                 │
│  Indexes: idx_tx_date_id, idx_tx_customer_normalized                 │
└─────────────────────────────────────────────────────────────────────┘
```

### Exception handling flow

```
Any @RestController method throws...
  │
  ├── ResourceNotFoundException
  │     → 404 Not Found
  │
  ├── BusinessRuleException(status, message)
  │     → caller-specified status (e.g. 409 Conflict)
  │
  ├── MethodArgumentNotValidException (Bean Validation)
  │     → 400 Bad Request + fieldErrors[]
  │
  ├── ConstraintViolationException
  │     → 400 Bad Request
  │
  ├── HttpMessageNotReadableException (malformed JSON)
  │     → 400 Bad Request
  │
  └── Exception (catch-all)
        → 500 Internal Server Error (generic message, no stack trace)
```

---

## 3. Request Flow Diagrams

### POST /api/transactions (happy path)

```
Client → POST /api/transactions { amountInPesos, merchant, customerName, transactionDate }
  │
  ▼ RateLimitFilter
  │  resolveKey(request) → "client-ip"
  │  limiter.tryConsume("client-ip") → RateLimitDecision.allowed(remaining)
  │  set X-Rate-Limit-* headers, continue filter chain
  │
  ▼ TransactionController.create(@Valid @RequestBody TransactionRequest)
  │  Bean Validation runs → if fails → GlobalExceptionHandler → 400
  │  calls service.create(request)
  │
  ▼ TransactionService.create(request)
  │  canonicalize(request.customerName) → normalized
  │  repo.countByCustomerNameNormalized(normalized) → check < 100
  │  if >= 100 → throw BusinessRuleException(409, "limit reached")
  │  applyRequest(new Transaction(), request) → sanitize + map fields
  │  @PrePersist sets customerNameNormalized
  │  repo.save(transaction) → PostgreSQL INSERT
  │  mapper.toResponse(saved) → TransactionResponse
  │
  ▼ TransactionController.create (continued)
  │  return ResponseEntity.created(location).body(response)
  │  Location: /api/transactions/{id}
  │
  ▼ Client receives 201 Created + JSON body
```

### GET /api/transactions?customerName=X (list flow)

```
Client → GET /api/transactions?customerName=Camila%20Torres
  │
  ▼ RateLimitFilter → allowed (decrements counter)
  │
  ▼ TransactionController.list(@RequestParam Optional<String> customerName)
  │
  ▼ TransactionService.list(customerName)
  │  if customerName present:
  │    canonicalize(customerName) → normalized
  │    repo.findByCustomerNameNormalized(normalized, Sort.by(date desc, id desc))
  │  else:
  │    repo.findAll(Sort.by(date desc, id desc))
  │  mapper.toResponse(each) → List<TransactionResponse>
  │
  ▼ 200 OK + JSON array
```

---

## 4. Domain Rules

| Rule | Where enforced | Error |
|---|---|---|
| Amount ≥ 0 | `@Min(0)` on `TransactionRequest.amountInPesos` | 400 fieldError |
| Amount ≤ Integer.MAX_VALUE | `@Max(2147483647)` on `TransactionRequest.amountInPesos` | 400 fieldError |
| Merchant required, ≤ 160 chars | `@NotBlank @Size(max=160)` | 400 fieldError |
| Customer name required, ≤ 120 chars | `@NotBlank @Size(max=120)` | 400 fieldError |
| Transaction date not in future | `@PastOrPresent` | 400 fieldError |
| Max 100 transactions per customer | `TransactionService.create()` quota check | 409 Conflict |
| No negative amount at DB level | `CHECK (amount_in_pesos >= 0)` | DB constraint (last resort) |

### `customerNameNormalized` — why it exists

When comparing or counting customer names, using `LOWER(customer_name)` in SQL prevents indexes from being used. Instead, a pre-computed `customer_name_normalized` column stores `lowercase + collapsed spaces`. The `@PrePersist` / `@PreUpdate` JPA hooks keep it in sync automatically on every save.

---

## 5. Data Model

```sql
CREATE TABLE transactions (
    id                       SERIAL       PRIMARY KEY,
    amount_in_pesos          INTEGER      NOT NULL CHECK (amount_in_pesos >= 0),
    merchant                 VARCHAR(160) NOT NULL,
    customer_name            VARCHAR(120) NOT NULL,
    customer_name_normalized  VARCHAR(120) NOT NULL,
    transaction_date         TIMESTAMP    NOT NULL
);

-- Covers: ORDER BY transaction_date DESC, id DESC (default listing)
CREATE INDEX idx_tx_date_id ON transactions (transaction_date DESC, id DESC);

-- Covers: WHERE customer_name_normalized = ? (filter + quota count)
CREATE INDEX idx_tx_customer_normalized ON transactions (customer_name_normalized);
```

---

## 6. API Summary

Base path: `/api/transactions`

| Method | Path | Success | Error conditions |
|---|---|---|---|
| `GET` | `/api/transactions` | 200 array | 429 rate limit |
| `GET` | `/api/transactions?customerName=X` | 200 filtered array | 429 |
| `GET` | `/api/transactions/{id}` | 200 single | 404, 429 |
| `POST` | `/api/transactions` | 201 + Location | 400 validation, 409 quota, 429 |
| `PUT` | `/api/transactions/{id}` | 200 updated | 400 validation, 404, 429 |
| `DELETE` | `/api/transactions/{id}` | 204 No Content | 404, 429 |

Example request body:

```json
{
  "amountInPesos": 15000,
  "merchant": "Supermercado Lider",
  "customerName": "Camila Torres",
  "transactionDate": "2026-03-06T11:30:00"
}
```

Swagger UI: `http://localhost:8080/swagger-ui`
OpenAPI JSON: `http://localhost:8080/v3/api-docs`

---

## 7. Error Model

```json
{
  "timestamp": "2026-03-06T19:45:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for the request body.",
  "path": "/api/transactions",
  "fieldErrors": [
    { "field": "amountInPesos", "message": "Transaction amount cannot be negative." }
  ]
}
```

`fieldErrors` is present only for Bean Validation failures (400). All other errors have an empty array. The 500 handler returns a generic message — stack traces are logged server-side only.

---

## 8. Rate Limiting

### Algorithm: fixed-window counter

```
Window: 60 seconds (PT1M by default)
Capacity: 3 requests per window per client

On each request to /api/**:
  1. Resolve client key (X-Forwarded-For → first IP, or remote address)
  2. Load FixedWindowCounter from Caffeine cache (auto-created per key)
  3. If now − windowStart ≥ duration → reset counter, set windowStart = now
  4. If count < capacity → count++ → allow (set X-Rate-Limit-Remaining)
  5. Else → block → return 429 JSON + Retry-After header
```

### Response headers (all requests to /api/**)

| Header | Value |
|---|---|
| `X-Rate-Limit-Limit` | Configured capacity (default 3) |
| `X-Rate-Limit-Remaining` | Remaining requests in this window |
| `Retry-After` | Seconds until window resets (only on 429) |

### Security note: X-Forwarded-For

`ClientKeyResolver` trusts the first `X-Forwarded-For` value. A client can spoof this header. For a single-node challenge this is acceptable; in production, configure a trusted proxy to overwrite the header or use a dedicated API gateway.

---

## 9. SOLID Analysis

### SRP (Single Responsibility)

Each class has exactly one reason to change:

- `TransactionController` — HTTP routing and status codes only. Change when HTTP API contract changes.
- `TransactionService` — Business rules only. Change when business logic changes.
- `TransactionRepository` — Queries only. Change when data access patterns change.
- `TransactionMapper` — Entity/DTO mapping only. Change when field shapes diverge.
- `GlobalExceptionHandler` — Error response shape only.
- `RateLimitFilter` — Request pipeline enforcement only.
- `ClientRateLimiter` — Counter algorithm only.
- `ClientKeyResolver` — IP extraction only.

### OCP (Open/Closed)

- `GlobalExceptionHandler` is open for extension (new `@ExceptionHandler` method) and closed for modification (existing handlers unchanged).
- `BusinessRuleException` lets any new domain rule throw with any HTTP status — no handler change needed.

### LSP (Liskov Substitution)

- `ResourceNotFoundException` and `BusinessRuleException` both extend `RuntimeException`. They behave correctly when caught by any `RuntimeException` handler.

### ISP (Interface Segregation)

- `TransactionService` calls only the methods it needs from `TransactionRepository`; it does not depend on the full `JpaRepository` contract.

### DIP (Dependency Inversion)

- All dependencies are injected via constructors (no field `@Autowired`).
- `TransactionController` depends on `TransactionService` as a Spring bean, not on the concrete class directly.
- `RateLimitFilter` depends on `ClientRateLimiter` and `ClientKeyResolver` beans.

---

## 10. Security Notes

| Concern | Implementation |
|---|---|
| CORS | Allowlist from env var; fail-closed if empty |
| Input validation | Bean Validation + Zod (frontend); DB CHECK (last resort) |
| Error info leakage | 500 handler returns generic message; no stack trace in response |
| Rate limit bypass | X-Forwarded-For spoofing is a known limitation (documented) |
| SQL injection | JPA/Hibernate parameterized queries; no raw SQL in production code |
| Integer overflow | `Math.toIntExact()` in `TransactionService.applyRequest()` |

---

## 11. Performance Notes

| Technique | Effect |
|---|---|
| `@Transactional(readOnly = true)` on list/get | Hibernate skips dirty-checking; enables read replica routing |
| Compound index `(transaction_date DESC, id DESC)` | Covers the ORDER BY; no full-table sort |
| Single-column index `(customer_name_normalized)` | Quota count is O(log n) per customer |
| Pre-computed `customerNameNormalized` | Index used without runtime function call |
| Caffeine rate-limit cache | Sub-microsecond in-memory reads; no external network hop |
| HikariCP (Spring Boot default) | DB connection reuse; no per-request connect overhead |

---

## 12. Configuration

| Variable | Purpose | Default |
|---|---|---|
| `POSTGRES_DB` | DB name | `tenpo_challenge` |
| `POSTGRES_USER` | DB user | `tenpo` |
| `POSTGRES_PASSWORD` | DB password | `tenpo` |
| `POSTGRES_PORT` | Host port for Postgres | `5432` |
| `DB_URL` | Spring datasource JDBC URL | `jdbc:postgresql://postgres:5432/tenpo_challenge` |
| `DB_USERNAME` | Spring datasource username | `tenpo` |
| `DB_PASSWORD` | Spring datasource password | `tenpo` |
| `BACKEND_PORT` | Host port for the API | `8080` |
| `APP_CORS_ALLOWED_ORIGINS` | Comma-separated browser origins | localhost defaults |
| `APP_RATE_LIMIT_CAPACITY` | Requests per window | `3` |
| `APP_RATE_LIMIT_DURATION` | Window duration (ISO-8601) | `PT1M` |
| `BACKEND_IMAGE_NAME` | Docker image name | `tenpo-backend` |
| `IMAGE_TAG` | Docker image tag | `latest` |

Full list in [`.env.example`](.env.example) and [`src/main/resources/application.yml`](src/main/resources/application.yml).

---

## 13. Local Development

Prerequisites: Java 17+, a running PostgreSQL instance (or Docker).

```bash
# Run tests (uses H2 in-memory, no Docker needed)
./mvnw test

# Start the API (requires PostgreSQL)
./mvnw spring-boot:run
```

Start only the database via Docker (without the full stack):

```bash
docker compose up postgres
```

Default API URL: `http://localhost:8080`

---

## 14. Docker

Backend + database only:

```bash
docker compose up --build
```

| Service | URL |
|---|---|
| Backend API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui |
| PostgreSQL | localhost:5432 |

Stop: `docker compose down`
Stop and remove volumes: `docker compose down -v`

For the full stack (including frontend), use the root [`docker-compose.yml`](../docker-compose.yml).

---

## 15. Testing

```bash
./mvnw test
```

| Test class | Scope | What it verifies |
|---|---|---|
| `TransactionServiceTest` | Unit | Quota boundary (99 vs 100), create/update/delete, name canonicalization, integer overflow guard |
| `TransactionRepositoryTest` | Integration (H2) | Derived queries, `@PrePersist` hook, filter by normalized name |
| `TransactionControllerTest` | Integration (MockMvc) | Status codes, Location header, 400 field error shape, @Max boundary |
| `RateLimitFilterIntegrationTest` | Integration (full context) | 3-allowed + 4th-blocked, Retry-After header, 429 JSON body |

H2 is configured in PostgreSQL compatibility mode so JPA and Flyway behave identically to production.

---

## 16. Manual Verification

1. Start the stack: `docker compose up --build`
2. Open `http://localhost:8080/swagger-ui`
3. Create a transaction (POST)
4. Fetch the list (GET)
5. Update the transaction (PUT)
6. Delete it (DELETE)
7. Test validation errors:
   - negative `amountInPesos`
   - future `transactionDate`
   - create more than 100 transactions for the same customer
8. Trigger rate limiting: call any `/api/**` endpoint more than 3 times within 60 seconds

---

## 17. Docker Hub Publication

```bash
# 1. Copy and configure env
cp .env.example .env
# Set: BACKEND_IMAGE_NAME=<your-user>/tenpo-backend

# 2. Build
docker compose build backend

# 3. Push
docker push <your-user>/tenpo-backend:latest
```
