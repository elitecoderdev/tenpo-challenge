# Tenpo Backend

This module contains the Spring Boot API for the Tenpo Full Stack Challenge.
Its responsibility is to expose a clean REST contract for transaction CRUD,
enforce business rules, persist data in PostgreSQL, document the API with Swagger,
and protect the service with clear error handling and per-client rate limiting.

The implementation follows the challenge intent closely:

- maintainable: layered package structure, DTO separation, centralized exceptions
- scalable enough for the challenge: stateless HTTP API, externalized configuration,
  Flyway migrations, isolated rate-limit component
- well documented: Swagger UI, root project README, and this backend-focused README
- testable: service, repository, controller, and rate-limit coverage

## What This Repo Covers

- `POST /api/transactions`
- `GET /api/transactions`
- `GET /api/transactions/{id}`
- `PUT /api/transactions/{id}`
- `DELETE /api/transactions/{id}`
- PostgreSQL persistence
- Flyway schema migration
- validation and business rule enforcement
- global API error format
- rate limiting: `3 requests / minute / client`
- Swagger UI at `http://localhost:8080/swagger-ui`

## Stack

- Java 17
- Spring Boot 3
- Spring Web
- Spring Validation
- Spring Data JPA
- PostgreSQL
- Flyway
- springdoc-openapi
- Caffeine for in-memory rate-limit state
- JUnit + Spring Boot Test

## Package Layout

```text
src/main/java/com/tenpo/challenge/
├── config/      # OpenAPI, CORS, and configuration properties
├── rate/        # Client key resolution, limiter, filter, settings
├── shared/      # API error model and shared exceptions
└── transaction/ # Entity, DTOs, mapper, repository, service, controller
```

Key implementation responsibilities:

- `transaction/TransactionService`
  business rules, orchestration, and mutation logic
- `transaction/TransactionController`
  REST entrypoints
- `shared/exception/GlobalExceptionHandler`
  consistent API error responses
- `rate/RateLimitFilter`
  request throttling for `/api/**`
- `config/OpenApiConfiguration`
  Swagger/OpenAPI metadata

## Domain Rules

The backend enforces the core rules from the challenge:

- a client can have at most `100` transactions
- transaction amounts cannot be negative
- transaction dates cannot be in the future
- malformed payloads return structured `400` responses
- missing records return `404`
- business-rule violations return structured client errors

The transaction model includes:

- `id`
- `amountInPesos`
- `merchant`
- `customerName`
- `transactionDate`

The persistence layer also stores `customer_name_normalized` to keep filtering and
counting stable even when spacing or casing changes.

## API Summary

Base path:

```text
/api/transactions
```

Endpoints:

- `GET /api/transactions`
- `GET /api/transactions/{id}`
- `POST /api/transactions`
- `PUT /api/transactions/{id}`
- `DELETE /api/transactions/{id}`

Example create payload:

```json
{
  "amountInPesos": 15000,
  "merchant": "Supermercado Lider",
  "customerName": "Camila Torres",
  "transactionDate": "2026-03-06T11:30:00"
}
```

Structured error shape:

```json
{
  "timestamp": "2026-03-07T00:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for the request body.",
  "path": "/api/transactions",
  "fieldErrors": [
    {
      "field": "amountInPesos",
      "message": "Transaction amount cannot be negative."
    }
  ]
}
```

Swagger:

- UI: `http://localhost:8080/swagger-ui`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Rate Limiting

The API applies a limit of `3 requests per minute per client` to `/api/**`.

Current approach:

- client key from `X-Forwarded-For` when present, otherwise remote IP
- in-memory fixed-window style limiter
- response headers:
  - `X-Rate-Limit-Limit`
  - `X-Rate-Limit-Remaining`
  - `Retry-After`

This is appropriate for a single-node coding challenge and is isolated enough to
be replaced later by Redis or gateway-based throttling if needed.

## Configuration

Default runtime configuration lives in:

- [`application.yml`](src/main/resources/application.yml)
- [`.env.example`](.env.example)
- [`docker-compose.yml`](docker-compose.yml)

Most relevant environment variables:

| Variable | Purpose | Default |
| --- | --- | --- |
| `POSTGRES_DB` | Postgres database name | `tenpo_challenge` |
| `POSTGRES_USER` | Postgres username | `tenpo` |
| `POSTGRES_PASSWORD` | Postgres password | `tenpo` |
| `POSTGRES_PORT` | Host port for Postgres | `5432` |
| `DB_URL` | Spring datasource JDBC URL | `jdbc:postgresql://postgres:5432/tenpo_challenge` |
| `DB_USERNAME` | Spring datasource username | `tenpo` |
| `DB_PASSWORD` | Spring datasource password | `tenpo` |
| `BACKEND_PORT` | Host port for the API | `8080` |
| `APP_CORS_ALLOWED_ORIGINS` | Allowed browser origins | localhost defaults |
| `BACKEND_IMAGE_NAME` | Docker image name | `tenpo-backend` |
| `IMAGE_TAG` | Docker image tag | `latest` |

## Local Development

Prerequisites:

- Java 17+
- a PostgreSQL instance, or Docker for the database

Run tests:

```bash
./mvnw test
```

Run the API locally:

```bash
./mvnw spring-boot:run
```

Default URL:

```text
http://localhost:8080
```

If you want PostgreSQL quickly without starting the full root stack, the simplest
path is:

```bash
docker compose up postgres
```

## Docker

This repo can run independently with:

```bash
docker compose up --build
```

What starts:

- PostgreSQL on `localhost:5432`
- backend API on `localhost:8080`

Stop:

```bash
docker compose down
```

Stop and remove DB volume:

```bash
docker compose down -v
```

If you want to override defaults, create a local `.env` file in this folder based on
[`.env.example`](.env.example).

## Testing

Current backend test suite:

- `TransactionServiceTest`
- `TransactionRepositoryTest`
- `TransactionControllerTest`
- `RateLimitFilterIntegrationTest`

These tests cover:

- service-level business rules
- repository persistence behavior
- controller HTTP behavior
- rate-limit enforcement

Run:

```bash
./mvnw test
```

## Manual Verification

1. Start the backend Docker stack.
2. Open `http://localhost:8080/swagger-ui`.
3. Create a transaction.
4. Fetch the list.
5. Update the transaction.
6. Delete the transaction.
7. Try invalid inputs:
   - negative amount
   - future date
   - more than 100 transactions for one client
8. Trigger rate limiting by calling the list endpoint more than 3 times within a minute.

## Docker Hub Publication

The backend image name and tag are externalized so this repo is already ready for publishing.

Example:

1. Copy [`.env.example`](.env.example) to `.env`
2. Set:

```text
BACKEND_IMAGE_NAME=<your-dockerhub-user>/tenpo-backend
IMAGE_TAG=latest
```

3. Build:

```bash
docker compose build backend
```

4. Push:

```bash
docker push <your-dockerhub-user>/tenpo-backend:latest
```
