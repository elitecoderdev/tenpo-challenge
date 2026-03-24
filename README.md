# Tenpo FullStack Challenge

A production-minded transaction management system built with **Spring Boot 3** (Java 17), **React 19** (TypeScript), **PostgreSQL 16**, and **Docker**.

---

## Table of Contents

1. [Requirement Coverage](#1-requirement-coverage)
2. [System Architecture](#2-system-architecture)
3. [Backend Module Map](#3-backend-module-map)
4. [Frontend Module Map](#4-frontend-module-map)
5. [Request Flow Diagrams](#5-request-flow-diagrams)
6. [Data Model](#6-data-model)
7. [SOLID Analysis](#7-solid-analysis)
8. [DRY Analysis](#8-dry-analysis)
9. [Security Analysis](#9-security-analysis)
10. [Performance Analysis](#10-performance-analysis)
11. [API Reference](#11-api-reference)
12. [Error Model](#12-error-model)
13. [Rate Limiting](#13-rate-limiting)
14. [Running the Project](#14-running-the-project)
15. [Testing](#15-testing)
16. [Docker Hub Publication](#16-docker-hub-publication)

---

## 1. Requirement Coverage

| Challenge requirement | Status | Implementation |
|---|---|---|
| Create transactions | ✅ Done | `POST /api/transactions` → `TransactionController` → `TransactionService` |
| Edit transactions | ✅ Done | `PUT /api/transactions/{id}` + side-panel editor |
| Delete transactions | ✅ Done | `DELETE /api/transactions/{id}` + UI delete action |
| Fields: id, amount, merchant, Tenpista name, date | ✅ Done | `Transaction` entity, DTOs, Flyway migration, React form |
| Max 100 transactions per customer | ✅ Done | `TransactionService.create()` business rule |
| No negative amounts | ✅ Done | `@Min(0)` (backend) + `z.number().min(0)` (frontend) |
| No future dates | ✅ Done | `@PastOrPresent` (backend) + Zod refine (frontend) |
| Spring Boot REST API | ✅ Done | `tenpo-backend` module |
| PostgreSQL | ✅ Done | datasource config + Flyway migrations |
| Rate limit 3 req/min/client | ✅ Done | `RateLimitFilter` + `ClientRateLimiter` (Caffeine) |
| Unit tests: service, repository, controller | ✅ Done | 4 test classes in `src/test/java` |
| Global HTTP error handler | ✅ Done | `GlobalExceptionHandler` (`@RestControllerAdvice`) |
| Swagger / OpenAPI + `/swagger-ui` | ✅ Done | springdoc-openapi configuration |
| React responsive frontend | ✅ Done | `tenpo-frontend` (Vite + React 19 + TypeScript) |
| Axios HTTP | ✅ Done | shared Axios instance in `src/app/api.ts` |
| React Query / cache (bonus) | ✅ Done | `@tanstack/react-query` v5, optimistic cache updates |
| Frontend form validation | ✅ Done | `zod` schema + `react-hook-form` |
| Docker (3 containers) | ✅ Done | Dockerfiles + root-level and repo-level Compose files |
| README with setup and API usage | ✅ Done | this document |

---

## 2. System Architecture

### Container topology

```
┌──────────────────────────────────────────────────────────────────┐
│  docker-compose.yml  (root)                                      │
│                                                                  │
│  ┌─────────────────┐     ┌──────────────────┐                   │
│  │   tenpo-frontend│     │   tenpo-backend   │                   │
│  │   nginx + SPA   │────▶│  Spring Boot 3    │                   │
│  │   port 3000     │     │  port 8080        │                   │
│  └─────────────────┘     └────────┬─────────┘                   │
│                                   │ JDBC                         │
│                          ┌────────▼─────────┐                   │
│                          │     postgres      │                   │
│                          │  PostgreSQL 16    │                   │
│                          │  port 5432        │                   │
│                          └──────────────────┘                   │
└──────────────────────────────────────────────────────────────────┘
```

### Request path (browser → API)

```
Browser
  │
  │ HTTP GET/POST/PUT/DELETE /api/transactions[/{id}]
  │ Header: X-API-Key: <api-key>
  ▼
Nginx (tenpo-frontend container)
  │  /api/** → proxy_pass http://backend:8080
  │  /*      → serve index.html (SPA routing)
  ▼
Spring Boot (tenpo-backend container)
  │
  ├─ ApiKeyAuthFilter (@Order 1, OncePerRequestFilter)
  │    checks X-API-Key header against app.security.api-key
  │    if missing or wrong: returns 401 JSON immediately
  │    adds X-Content-Type-Options, X-Frame-Options, Cache-Control headers
  │
  ├─ RateLimitFilter (@Order 2, OncePerRequestFilter)
  │    resolves client IP → checks Caffeine counter
  │    if blocked: returns 429 JSON immediately
  │
  ├─ DispatcherServlet → TransactionController
  │    validates path/query params
  │
  ├─ TransactionService
  │    applies business rules (quota, sanitize, convert)
  │
  ├─ TransactionRepository (Spring Data JPA)
  │    executes SQL queries with indexes
  │
  └─ PostgreSQL 16
       transactions table with Flyway-managed schema
```

### Layer separation

```
┌─────────────────────────────────────────────────┐
│  Presentation Layer (TransactionController)      │
│  HTTP contract, status codes, Location header    │
├─────────────────────────────────────────────────┤
│  Application Layer (TransactionService)          │
│  Business rules: quota, sanitize, validate       │
├─────────────────────────────────────────────────┤
│  Persistence Layer (TransactionRepository)       │
│  Spring Data JPA derived queries + indexes       │
├─────────────────────────────────────────────────┤
│  Database (PostgreSQL 16 + Flyway)               │
│  Versioned migrations, CHECK constraints         │
└─────────────────────────────────────────────────┘
```

---

## 3. Backend Module Map

### Package structure

```
com.tenpo.challenge
├── TenpobackApplication.java          ← Spring Boot entry point
│
├── transaction/                       ← Domain aggregate
│   ├── Transaction.java               ← JPA entity (@Entity, @PrePersist/@PreUpdate)
│   ├── TransactionRequest.java        ← Input DTO with Bean Validation (@Valid)
│   ├── TransactionResponse.java       ← Output DTO (excludes internal fields)
│   ├── TransactionMapper.java         ← Entity ↔ DTO mapping (SRP)
│   ├── TransactionRepository.java     ← Spring Data JPA (derived queries + @Query)
│   ├── TransactionService.java        ← Business logic (quota, sanitize, convert)
│   └── TransactionController.java     ← REST controller (5 endpoints)
│
├── shared/
│   ├── api/
│   │   ├── ApiError.java              ← Immutable error response record
│   │   ├── ApiErrorFactory.java       ← Builds ApiError with timestamp
│   │   └── ApiFieldError.java         ← Per-field validation error record
│   └── exception/
│       ├── BusinessRuleException.java ← Domain exception with HttpStatus
│       ├── ResourceNotFoundException.java ← Always → 404
│       └── GlobalExceptionHandler.java ← @RestControllerAdvice, 6 handlers
│
├── rate/
│   ├── ClientRateLimiter.java         ← Fixed-window algorithm (Caffeine)
│   ├── RateLimitFilter.java           ← OncePerRequestFilter (applies limit)
│   ├── RateLimitDecision.java         ← Value record: allowed/blocked + headers
│   ├── ClientKeyResolver.java         ← Extracts client IP (X-Forwarded-For)
│   └── RateLimitProperties.java       ← @ConfigurationProperties (capacity, duration)
│
└── config/
    ├── WebConfiguration.java          ← CORS allowlist (allowedOrigins from env)
    ├── CorsProperties.java            ← @ConfigurationProperties for CORS origins
    └── OpenApiConfiguration.java      ← Swagger / springdoc-openapi setup
```

### Class relationships

```
TransactionController
    │ depends on (DIP: constructor injection)
    ▼
TransactionService
    │ uses
    ├──▶ TransactionRepository  (Spring Data JPA interface)
    │        │ backed by
    │        └──▶ PostgreSQL (via HikariCP + Hibernate)
    │
    └──▶ TransactionMapper      (entity ↔ DTO)

GlobalExceptionHandler
    │ catches
    ├──▶ ResourceNotFoundException  → 404
    ├──▶ BusinessRuleException      → caller-specified status
    ├──▶ MethodArgumentNotValidException → 400 with fieldErrors
    └──▶ Exception                  → 500 (generic, no stack trace)

RateLimitFilter
    │ delegates to
    ├──▶ ClientKeyResolver     (resolve IP)
    └──▶ ClientRateLimiter     (check counter)
             │ backed by
             └──▶ Caffeine cache (in-memory, per-window-counter)
```

### Database migration (Flyway)

```sql
-- V1__create_transactions.sql
CREATE TABLE transactions (
    id                      SERIAL PRIMARY KEY,
    amount_in_pesos         INTEGER NOT NULL CHECK (amount_in_pesos >= 0),
    merchant                VARCHAR(160) NOT NULL,
    customer_name           VARCHAR(120) NOT NULL,
    customer_name_normalized VARCHAR(120) NOT NULL,
    transaction_date        TIMESTAMP NOT NULL
);

-- idx_tx_date_id: primary sort path for listing (ORDER BY date DESC, id DESC)
CREATE INDEX idx_tx_date_id ON transactions (transaction_date DESC, id DESC);

-- idx_tx_customer_normalized: customer filter + per-customer quota count
CREATE INDEX idx_tx_customer_normalized ON transactions (customer_name_normalized);
```

---

## 4. Frontend Module Map

### Directory structure

```
src/
├── main.tsx                           ← Entry point (QueryClient, StrictMode, mount)
├── App.tsx                            ← Root component (all state, mutations, layout)
├── App.css                            ← Global styles
├── index.css                          ← CSS reset + design tokens
│
├── app/
│   └── api.ts                         ← Shared Axios instance (base URL, interceptors)
│
├── components/
│   └── Modal.tsx                      ← Generic accessible modal (createPortal, ARIA)
│
├── features/
│   └── transactions/
│       ├── types.ts                   ← TypeScript interfaces (Transaction, ApiError, …)
│       ├── schema.ts                  ← Zod validation schema + form helpers
│       ├── queries.ts                 ← React Query keys, query options, mutation fns
│       ├── TransactionList.tsx        ← List renderer + card components
│       └── TransactionForm.tsx        ← Controlled form (create + edit, dual mode)
│
└── lib/
    └── formatters.ts                  ← Shared formatters (formatCLP — DRY fix)
```

### Component tree

```
App
├── [hero section]          hero actions: New transaction, Refresh
├── [stats grid]            4 derived stat cards (count, amount, customers, latest)
├── [workspace]
│   ├── TransactionList     renders each Transaction as a card
│   │   └── [card ×N]       edit button, delete button, avatar, amount, date
│   └── aside panel
│       ├── TransactionForm  (edit mode — activeTransaction is set)
│       └── [empty state]   (standby — no activeTransaction)
└── Modal
    └── TransactionForm      (create mode — activeTransaction is null)
```

### State flow

```
App state
├── transactions[]          ← React Query cache (fetched once, TTL 60 s)
├── activeTransaction       ← which card is open in the side panel
├── isCreateModalOpen       ← controls Modal visibility
├── serverError             ← last API error from a failed mutation
├── draftCustomerFilter     ← raw filter input (high priority)
├── deferredCustomerFilter  ← deferred copy (low priority, used for filtering)
└── toast                   ← ephemeral success message

Derived values (no extra requests)
├── visibleTransactions     ← filtered by deferredCustomerFilter
├── totalAmount             ← sum of visibleTransactions
├── uniqueCustomers         ← distinct customer count
└── latestTransaction       ← visibleTransactions[0]
```

### React Query cache strategy

```
Initial load
  useQuery(transactionsQueryOptions)
    │ staleTime: 60 000 ms → no background refetch for 1 minute
    │ gcTime: 600 000 ms   → cache survives 10 minutes of inactivity
    │ retry: 0             → no automatic retries (preserves rate limit)
    │ refetchOnWindowFocus: false
    └─ refetchOnReconnect: false

After create mutation
  queryClient.setQueryData(transactionKeys.all, [...existing, newTx])
  → sorted in place, no network request

After update mutation
  queryClient.setQueryData(transactionKeys.all,
    existing.map(tx => tx.id === updatedTx.id ? updatedTx : tx))
  → re-sorted in place, no network request

After delete mutation
  queryClient.setQueryData(transactionKeys.all,
    existing.filter(tx => tx.id !== deletedId))
  → removed in place, no network request

Manual sync
  refetch() button → forces one network request
```

---

## 5. Request Flow Diagrams

### Create transaction

```
[Browser]
  │ User fills form, clicks "Create transaction"
  │
  ▼
[TransactionForm]
  │ react-hook-form validates against transactionFormSchema (Zod)
  │ toPayload() → normalizes whitespace, formats date as YYYY-MM-DDTHH:mm:ss
  │
  ▼
[App.handleSubmit → createMutation.mutateAsync(payload)]
  │
  ▼ POST /api/transactions
[Nginx proxy]
  │
  ▼
[RateLimitFilter]
  │ resolves IP → checks Caffeine counter → decrements remaining
  │ if limit reached → 429 JSON response, flow stops here
  │
  ▼
[TransactionController.create()]
  │ @RequestBody @Valid TransactionRequest → Bean Validation
  │ if invalid → GlobalExceptionHandler → 400 fieldErrors
  │
  ▼
[TransactionService.create()]
  │ countByCustomerNameNormalized() → checks 100-tx quota
  │ if quota reached → BusinessRuleException → 409
  │ sanitizeText(merchant), sanitizeText(customerName)
  │ Math.toIntExact(amountInPesos) → overflow guard
  │ save() → @PrePersist sets customerNameNormalized
  │
  ▼
[PostgreSQL]
  │ INSERT INTO transactions (…) RETURNING id
  │
  ▼ 201 Created + Location: /api/transactions/{id}
[App.createMutation.onSuccess()]
  │ queryClient.setQueryData() → insert + re-sort cache
  │ setActiveTransaction(createdTx) → open side panel
  └─ showToast("Transaction created successfully")
```

### List + filter (client-side, no extra request)

```
[Browser]
  │ User types in "Filter by Tenpista name" input
  │
  ▼
[App]
  │ setDraftCustomerFilter(value)   ← high priority, instant
  │ deferredCustomerFilter          ← React schedules at low priority
  │
  ▼
[visibleTransactions]
  │ transactions.filter(tx =>
  │   tx.customerName.toLowerCase().includes(deferredFilter))
  │
  ▼
[TransactionList] re-renders with filtered cards
  │
  └─ Stats cards also recompute (totalAmount, uniqueCustomers, latestTransaction)
     ← all derived, zero network requests
```

### Rate limit (429 flow)

```
[Browser] → 4th request within 60 s window
  │
  ▼
[RateLimitFilter]
  │ ClientKeyResolver.resolve(request) → "192.168.1.1"
  │ ClientRateLimiter.tryConsume("192.168.1.1")
  │   → FixedWindowCounter.count == 3  (limit reached)
  │   → returns RateLimitDecision.blocked(retryAfterMs)
  │
  ▼ Short-circuit: write JSON body, set headers, return
  │   HTTP/1.1 429 Too Many Requests
  │   X-Rate-Limit-Limit: 3
  │   X-Rate-Limit-Remaining: 0
  │   Retry-After: <seconds until window resets>
  │
  ▼
[App.createMutation.onError()]
  │ extractApiError() → ApiError{status:429, message:"Rate limit exceeded"}
  └─ setServerError() → shown in TransactionForm as server-error banner
```

---

## 6. Data Model

### Entity: `Transaction`

| Column | SQL Type | Constraints | Notes |
|---|---|---|---|
| `id` | `SERIAL` | `PRIMARY KEY` | Auto-incremented by PostgreSQL |
| `amount_in_pesos` | `INTEGER` | `NOT NULL, CHECK >= 0` | Backend: `@Min(0) @Max(2147483647)` |
| `merchant` | `VARCHAR(160)` | `NOT NULL` | Backend sanitizes whitespace |
| `customer_name` | `VARCHAR(120)` | `NOT NULL` | Original casing preserved |
| `customer_name_normalized` | `VARCHAR(120)` | `NOT NULL` | Lowercase, collapsed spaces (for indexed filter + count) |
| `transaction_date` | `TIMESTAMP` | `NOT NULL` | `@PastOrPresent` — no future dates |

### Indexes

| Name | Columns | Purpose |
|---|---|---|
| `idx_tx_date_id` | `(transaction_date DESC, id DESC)` | Covers the default ORDER BY in listing queries |
| `idx_tx_customer_normalized` | `(customer_name_normalized)` | Per-customer quota count + `customerName` filter |

### DTO shapes

```
TransactionRequest (input)              TransactionResponse (output)
─────────────────────────────────       ─────────────────────────────────
long   amountInPesos  @Min(0)          Integer id
String merchant       @NotBlank        Integer amountInPesos
String customerName   @NotBlank        String  merchant
String transactionDate @PastOrPresent  String  customerName
                                       String  transactionDate
                       (customerNameNormalized intentionally excluded)
```

---

## 7. SOLID Analysis

### Single Responsibility Principle (SRP)

| Class | Responsibility | What it does NOT do |
|---|---|---|
| `TransactionController` | HTTP contract (routing, status codes, headers) | No business logic, no DTO mapping |
| `TransactionService` | Business rules (quota, sanitize, convert) | No HTTP concerns, no entity-to-DTO mapping |
| `TransactionRepository` | Database queries | No business rules |
| `TransactionMapper` | Entity ↔ DTO conversion | No persistence, no validation |
| `GlobalExceptionHandler` | Error response shaping | No domain logic |
| `RateLimitFilter` | Rate-limit enforcement (HTTP pipeline) | No rate-limit algorithm |
| `ClientRateLimiter` | Fixed-window counter algorithm | No HTTP concerns |
| `ClientKeyResolver` | Client IP extraction | No decision making |
| `TransactionForm` (React) | Form state + rendering | No API calls, no cache management |
| `TransactionList` (React) | Card rendering | No data fetching, no mutation |
| `formatters.ts` | Currency formatting | No rendering, no state |

### Open/Closed Principle (OCP)

- **`GlobalExceptionHandler`**: new exception types can be handled by adding a new `@ExceptionHandler` method without touching existing handlers.
- **`BusinessRuleException`**: new business rules can throw this with any `HttpStatus` — the handler needs no change.
- **`TransactionForm`**: new form fields are added by extending the Zod schema and adding a JSX input — no structural change to the form logic.

### Liskov Substitution Principle (LSP)

- `ResourceNotFoundException` and `BusinessRuleException` both extend `RuntimeException`. They can be caught by `catch(RuntimeException)` in any context without breaking behavior.

### Interface Segregation Principle (ISP)

- `TransactionRepository` extends `JpaRepository<Transaction, Integer>`. The service only uses `save()`, `findById()`, `deleteById()`, `findAll()`, and the two derived methods — it does not depend on the full JPA contract directly.

### Dependency Inversion Principle (DIP)

- All Spring beans are injected via constructor injection (no `@Autowired` on fields).
- `TransactionController` depends on the `TransactionService` abstraction (bean interface), not on a concrete class.
- `RateLimitFilter` depends on `ClientRateLimiter` and `ClientKeyResolver` abstractions.
- React components receive data and callbacks as props — they do not call APIs directly.

---

## 8. DRY Analysis

### Shared currency formatter (`lib/formatters.ts`)

Before this fix, `Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 })` was instantiated independently in both `App.tsx` and `TransactionList.tsx`. A change to the locale or options would have required editing two files and risked divergence.

**Fix**: extracted into `src/lib/formatters.ts` as `formatCLP(amount: number): string`. Both `App.tsx` and `TransactionList.tsx` now import this function. The `Intl.NumberFormat` object is a module-level constant — created once on import, not on every render.

### Backend `applyRequest()` helper

`TransactionService.applyRequest()` is a private method shared by both `create()` and `update()`. It holds the field-mapping logic (sanitizeText, Math.toIntExact, date parsing) in a single place. Without it, those four lines would be duplicated in both methods.

### `transactionKeys` cache key factory (`queries.ts`)

All React Query cache operations reference `transactionKeys.all`. Changing the key string requires editing one line.

### `ApiErrorFactory`

Builds `ApiError` records with a consistent timestamp and reason phrase. Every exception handler calls the same factory instead of constructing the record inline.

### `toPayload()` and `getInitialFormValues()` (`schema.ts`)

Form-to-payload conversion and initial-value computation are defined once in `schema.ts` and called from both `TransactionForm` (for initial state) and `App.tsx` (the submit callback). The whitespace normalization (`.trim().replace(/\s+/g, ' ')`) mirrors the backend's `sanitizeText()` in a single place.

---

## 9. Security Analysis

### API Key Authentication

`ApiKeyAuthFilter` (`@Order(1)`) intercepts every `/api/**` request and validates the `X-API-Key` header against the value configured in `app.security.api-key` (env var `APP_API_KEY`, default `tenpo-dev-key`).

- Missing key → HTTP 401 with structured `ApiError` JSON body
- Wrong key → HTTP 401 (different message)
- Correct key → request continues to `RateLimitFilter`
- Bypassed for: `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/**`

Security headers added to every response: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Cache-Control: no-store`.

The frontend Axios instance reads `VITE_API_KEY` (env var, default `tenpo-dev-key`) and sends it as `X-API-Key` on every request automatically.

### CORS

`WebConfiguration` builds the CORS allowlist from `app.cors.allowed-origins` (env var). If the list is empty, no origins are mapped — the behavior is **fail-closed** (all cross-origin requests blocked). In Docker Compose the default allows `localhost:3000` and `localhost:5173` only.

### Input validation (defense in depth)

| Layer | Mechanism |
|---|---|
| Frontend | Zod schema with `refine()` for date rules; `react-hook-form` prevents submission until valid |
| HTTP | Bean Validation (`@Valid` on `@RequestBody`); `GlobalExceptionHandler` returns 400 with field errors |
| Service | `Math.toIntExact()` guards against integer overflow; `sanitizeText()` collapses whitespace |
| Database | `CHECK (amount_in_pesos >= 0)` constraint as a last line of defense |

### Error information exposure

`GlobalExceptionHandler` catches bare `Exception` and returns a **generic message** (`"An unexpected error occurred."`) with no stack trace, class names, or internal paths. The real exception is logged server-side only.

### Rate limiting

- Applies to `/api/**` only (not to Swagger UI or Actuator paths).
- Returns structured JSON (not an HTML error page) for programmatic consumption.
- Sets `Retry-After` header so well-behaved clients can back off.

### Known limitation: X-Forwarded-For spoofing

`ClientKeyResolver` trusts the first value of the `X-Forwarded-For` header. A malicious client can spoof this header to bypass per-IP rate limiting. This is an **accepted trade-off** for a single-node challenge — in a production setup behind a trusted proxy (e.g. AWS ALB), you would configure the proxy to overwrite (not append) the header, or use a dedicated API gateway for rate limiting.

---

## 10. Performance Analysis

### Backend

| Technique | Where | Effect |
|---|---|---|
| `@Transactional(readOnly = true)` | `findAll()`, `findById()` | Hibernate skips dirty-checking; DB can use read replica if configured |
| Compound index `(transaction_date DESC, id DESC)` | `V1__create_transactions.sql` | Covers the ORDER BY clause; avoids a full-table sort |
| Single-column index `(customer_name_normalized)` | `V1__create_transactions.sql` | Makes per-customer quota count O(log n) instead of O(n) |
| `customerNameNormalized` column | `Transaction.java` | Pre-computed at write time; avoids `LOWER()` in queries, keeping the index usable |
| Caffeine cache for rate-limit counters | `ClientRateLimiter.java` | In-memory, sub-microsecond reads; no Redis round-trip for the challenge scope |
| HikariCP connection pool | Spring Boot default | Reuses DB connections across requests |

### Frontend

| Technique | Where | Effect |
|---|---|---|
| Fetch once, filter locally | `App.tsx` | Zero extra requests for customer filter — respects 3 req/min limit |
| `staleTime: 60 000 ms` | `main.tsx` QueryClient | Data younger than 1 minute is never re-fetched in background |
| `refetchOnWindowFocus: false` | `main.tsx` QueryClient | No request fired when user alt-tabs back |
| `refetchOnReconnect: false` | `main.tsx` QueryClient | No request on network reconnect |
| `retry: 0` | `main.tsx` QueryClient | No automatic retries (each retry costs 1 of 3 req/min) |
| Optimistic cache updates | `App.tsx` mutations | `setQueryData()` instead of `refetch()` after create/update/delete |
| Module-level `Intl.NumberFormat` | `lib/formatters.ts` | Created once at import time, not on every render |
| `useDeferredValue` on filter | `App.tsx` | Filter input stays responsive while list re-renders at lower priority |
| `useTransition` for panel state | `App.tsx` | Panel open/close does not block urgent renders |
| CSS animation stagger via `--stagger` | `TransactionList.tsx` | Stagger computed in CSS from a custom property — no JS animation loop |

---

## 11. API Reference

### Endpoints

| Method | Path | Status | Description |
|---|---|---|---|
| `GET` | `/api/transactions` | 200 | List all transactions (newest first) |
| `GET` | `/api/transactions?customerName=X` | 200 | Filter by customer name (case-insensitive) |
| `GET` | `/api/transactions/{id}` | 200 / 404 | Get a single transaction |
| `POST` | `/api/transactions` | 201 / 400 / 409 / 429 | Create a transaction |
| `PUT` | `/api/transactions/{id}` | 200 / 400 / 404 / 429 | Update a transaction |
| `DELETE` | `/api/transactions/{id}` | 204 / 404 / 429 | Delete a transaction |

### Request body (`POST` / `PUT`)

```json
{
  "amountInPesos": 15000,
  "merchant": "Supermercado Lider",
  "customerName": "Camila Torres",
  "transactionDate": "2026-03-06T11:30:00"
}
```

### Successful response (single transaction)

```json
{
  "id": 42,
  "amountInPesos": 15000,
  "merchant": "Supermercado Lider",
  "customerName": "Camila Torres",
  "transactionDate": "2026-03-06T11:30:00"
}
```

### cURL examples

```bash
# Create
curl -X POST http://localhost:8080/api/transactions \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: tenpo-dev-key' \
  -d '{"amountInPesos":21000,"merchant":"Restaurante","customerName":"Camila Torres","transactionDate":"2026-03-06T19:45:00"}'

# List
curl -H 'X-API-Key: tenpo-dev-key' http://localhost:8080/api/transactions

# Filter by customer
curl -H 'X-API-Key: tenpo-dev-key' 'http://localhost:8080/api/transactions?customerName=Camila%20Torres'

# Update
curl -X PUT http://localhost:8080/api/transactions/1 \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: tenpo-dev-key' \
  -d '{"amountInPesos":24000,"merchant":"Restaurante actualizado","customerName":"Camila Torres","transactionDate":"2026-03-06T20:15:00"}'

# Delete
curl -X DELETE -H 'X-API-Key: tenpo-dev-key' http://localhost:8080/api/transactions/1
```

### Swagger UI

```
http://localhost:8080/swagger-ui
```

OpenAPI JSON:

```
http://localhost:8080/v3/api-docs
```

---

## 12. Error Model

All handled errors return a structured JSON payload:

```json
{
  "timestamp": "2026-03-06T19:45:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for the request body.",
  "path": "/api/transactions",
  "fieldErrors": [
    { "field": "amountInPesos", "message": "Transaction amount cannot be negative." },
    { "field": "transactionDate", "message": "Transaction date cannot be in the future." }
  ]
}
```

| Scenario | Status | `fieldErrors`? |
|---|---|---|
| Bean validation failure | 400 | Yes — one entry per failing field |
| Malformed JSON / unparseable date | 400 | No |
| Transaction not found | 404 | No |
| Business rule violation (e.g. quota) | 409 | No |
| Rate limit exceeded | 429 | No |
| Unexpected server error | 500 | No (generic message, no stack trace) |

The frontend `normalizeApiError()` function tolerantly coerces any API error shape into a typed `ApiError` so the UI can always display a meaningful message.

---

## 13. Rate Limiting

### Algorithm

Fixed-window counter per client IP. Each `FixedWindowCounter` stores:

- `count` — requests made in the current window
- `windowStart` — the Instant when the window opened

When a request arrives:

1. If `now - windowStart >= window duration`, reset counter to 0 and set `windowStart = now`.
2. If `count < capacity`, increment and allow.
3. Else block and return `Retry-After = ceil((windowStart + duration - now) / 1 000)` seconds.

### Configuration

| Property | Default | Env override |
|---|---|---|
| `app.rate-limit.capacity` | `3` | `APP_RATE_LIMIT_CAPACITY` |
| `app.rate-limit.duration` | `PT1M` (1 minute) | `APP_RATE_LIMIT_DURATION` |

### Response headers

| Header | Meaning |
|---|---|
| `X-Rate-Limit-Limit` | Maximum requests allowed per window |
| `X-Rate-Limit-Remaining` | Requests remaining in the current window |
| `Retry-After` | Seconds until the window resets (only on 429) |

---

## 14. Running the Project

### Option A — Full stack from the project root (recommended)

```bash
docker compose up --build
```

| Service | URL |
|---|---|
| Frontend | http://localhost:3000 |
| Backend API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui |
| PostgreSQL | localhost:5432 |

Stop:

```bash
docker compose down
# or with volumes:
docker compose down -v
```

### Option B — Backend + database only

```bash
cd tenpo-backend
docker compose up --build
```

### Option C — Frontend against a running backend

```bash
cd tenpo-frontend
docker compose up --build
```

Requires the backend to already be running on `http://localhost:8080`.

### Option D — Local dev (no Docker)

```bash
# Backend (Java 17+ required)
cd tenpo-backend
./mvnw spring-boot:run

# Frontend (Node 20+ required)
cd tenpo-frontend
npm install
npm run dev   # → http://localhost:5173 (Vite proxies /api to :8080)
```

Environment variables are documented in `.env.example` files in each directory.

Key variables for API key authentication:

| Variable | Where | Default | Description |
|---|---|---|---|
| `APP_API_KEY` | `tenpo-backend/.env` | `tenpo-dev-key` | API key the backend accepts in the `X-API-Key` header |
| `VITE_API_KEY` | `tenpo-frontend/.env` | `tenpo-dev-key` | API key the frontend sends in the `X-API-Key` header |

Both values must match for the frontend to communicate with the backend.

---

## 15. Testing

### Backend (Maven)

```bash
cd tenpo-backend
./mvnw test
```

| Test class | What it covers |
|---|---|
| `TransactionServiceTest` | Create/update/delete business rules, quota boundary (99 vs 100), name canonicalization, integer overflow guard |
| `TransactionRepositoryTest` | Derived query methods, `@PrePersist` hook, index-backed filter |
| `TransactionControllerTest` | HTTP contract (status codes, Location header, 400 field errors, @Max boundary) |
| `RateLimitFilterIntegrationTest` | 3-allowed + 4th-blocked cycle, Retry-After header, 429 JSON body |

H2 in-memory database is used for tests (PostgreSQL compatibility mode). No Docker required.

### Frontend (TypeScript + Vite)

```bash
cd tenpo-frontend
npm run lint    # ESLint
npm run build   # tsc -b + vite build (type-check + bundle)
```

---

## 16. Docker Hub Publication

The backend image is tagged through Compose and is ready to publish:

1. Copy `tenpo-backend/.env.example` to `tenpo-backend/.env`
2. Set `BACKEND_IMAGE_NAME=<your-dockerhub-user>/tenpo-backend`
3. Build: `cd tenpo-backend && docker compose build backend`
4. Push: `docker push <your-dockerhub-user>/tenpo-backend:latest`

The project is fully containerized and can be pushed to any public registry with standard `docker build`, `docker tag`, and `docker push` commands.
