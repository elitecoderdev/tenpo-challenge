# Tenpo FullStack Challenge

This repository delivers the challenge as a production-minded Java + React application.
- Spring Boot REST API for transaction CRUD
- PostgreSQL persistence with Flyway migrations
- structured global HTTP error handling
- per-client rate limiting at 3 requests per minute
- Swagger / OpenAPI documentation at `/swagger-ui`
- React frontend using `axios` and `@tanstack/react-query`
- Dockerfiles plus root-level and repo-level Docker Compose entrypoints
- backend tests for service, repository, controller, and rate-limit integration

Complex code paths include concise comments in both English and Spanish.

## Objective Alignment

This solution was built to satisfy the challenge objective, not only the endpoint list.

- scalable for the challenge scope:
  stateless API, externalized configuration, Dockerized services, cache-aware frontend,
  isolated rate-limit component, Flyway-managed schema
- well documented:
  root README, detailed backend/frontend READMEs, Swagger UI, environment examples,
  and Docker run instructions
- maintainable:
  layered backend design, typed frontend modules, centralized error handling,
  validation on both sides, and automated tests around critical behavior

## 1. Requirement Coverage

| Challenge requirement | Status | Implementation |
| --- | --- | --- |
| Create transactions | Done | `POST /api/transactions`, backend service, React form |
| Edit transactions | Done | `PUT /api/transactions/{id}` + editor panel |
| Delete transactions | Done | `DELETE /api/transactions/{id}` + UI delete action |
| Required fields: id, amount, merchant, Tenpista name, date | Done | entity, DTOs, migration, UI |
| Max 100 transactions per client | Done | `TransactionService` business rule |
| No negative amounts | Done | backend bean validation + frontend schema |
| No future dates | Done | backend bean validation + frontend schema |
| Spring Boot REST API | Done | `tenpo-backend` |
| PostgreSQL | Done | datasource config + root/repo-level compose files |
| Rate limit 3 req/min/client | Done | `RateLimitFilter` + `ClientRateLimiter` |
| Unit tests for services, repositories, controllers | Done | `src/test/java/...` |
| Global HTTP error handler | Done | `GlobalExceptionHandler` |
| Swagger/OpenAPI + `/swagger-ui` | Done | springdoc configuration |
| React responsive frontend | Done | `tenpo-frontend` |
| Axios fetching | Done | `src/app/api.ts` |
| React Query / cache as plus | Done | query client + local cache updates |
| Frontend form validation | Done | `zod` + `react-hook-form` |
| Docker for backend, frontend, database | Done | Dockerfiles + root/repo-level Compose files |
| README with setup and API usage | Done | this document |

## 2. Architecture

### Backend

The backend is structured in layers:

- `transaction/`
  entity, repository, DTOs, mapper, service, controller
- `shared/api/`
  structured error payloads
- `shared/exception/`
  domain exceptions and global exception handler
- `rate/`
  rate-limit logic and request filter
- `config/`
  OpenAPI and CORS configuration

Important design choices:

- Flyway manages the schema, so startup is deterministic.
- Validation exists in both frontend and backend.
- The 100-transaction cap is a service-layer business rule.
- The UI filters locally after the initial fetch to preserve the 3 req/min limit.
- The cache is updated after mutations instead of refetching every time.

### Frontend

The frontend is a Vite + React + TypeScript SPA with:

- `axios` for HTTP
- `@tanstack/react-query` for request state and cache control
- `react-hook-form` + `zod` for validation
- a split UI:
  - left side: board, filter, summaries
  - right side: create/edit form

UI decisions:

- warm editorial visual language instead of a generic template dashboard
- local customer filtering to avoid wasting API quota
- manual refresh instead of aggressive background revalidation
- responsive layout for desktop and mobile

## 3. Project Structure

```text
challenge-java/
├── .env.example
├── docker-compose.yml
├── README.md
├── tenpo-backend/
│   ├── .env.example
│   ├── Dockerfile
│   ├── docker-compose.yml
│   ├── pom.xml
│   └── src/
│       ├── main/
│       └── test/
└── tenpo-frontend/
    ├── .env.example
    ├── Dockerfile
    ├── docker-compose.yml
    ├── nginx.conf
    ├── nginx.standalone.conf
    ├── package.json
    └── src/
```

## 4. Backend Details

### Data model

The `transactions` table contains:

- `id` integer primary key
- `amount_in_pesos` integer
- `merchant` varchar(160)
- `customer_name` varchar(120)
- `customer_name_normalized` varchar(120)
- `transaction_date` timestamp

The normalized name is stored so counting and filtering remain stable even when the input casing or spacing changes.

### Rate limiting

The challenge explicitly asks for `3 request por minuto por cliente`.

This implementation uses:

- client key from `X-Forwarded-For` or remote IP
- in-memory fixed-window limiter
- limit applied only to `/api/**`
- response headers:
  - `X-Rate-Limit-Limit`
  - `X-Rate-Limit-Remaining`
  - `Retry-After`

Why this approach:

- it is clean and sufficient for a single-instance challenge
- the code is isolated and easy to replace with Redis or an API gateway later

### Error model

All handled errors return a structured payload:

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

Handled scenarios:

- validation failures
- not found
- business rule conflicts
- malformed JSON / invalid date formats
- generic `500`

### Swagger

Swagger UI:

```text
http://localhost:8080/swagger-ui
```

OpenAPI JSON:

```text
http://localhost:8080/v3/api-docs
```

## 5. Frontend Details

### Why the frontend is cache-first

The rate limit is intentionally strict. A default SPA that refetches on focus, on reconnect, after every mutation, and while filtering would hit the limit very quickly.

So this UI intentionally:

- fetches the list once on load
- filters by customer locally
- updates the React Query cache after create/update/delete
- disables noisy automatic refetch behavior
- exposes a manual refresh button for deliberate sync

That is a better engineering tradeoff than pretending every screen should constantly refetch under a 3 req/min/client contract.

### Form validation

Frontend validation enforces:

- non-negative amount
- required merchant
- required customer name
- valid date-time
- no future date-time

The backend enforces the same rules, so the API remains safe without the UI.

## 6. Local Run Without Docker

### Backend

Prerequisites:

- Java 17+

Run:

```bash
cd tenpo-backend
./mvnw test
./mvnw spring-boot:run
```

Default backend URL:

```text
http://localhost:8080
```

The backend expects PostgreSQL by default. For a quick local environment, start the database through Docker Compose first.

### Frontend

Prerequisites:

- Node.js 23.x or a recent LTS release

Run:

```bash
cd tenpo-frontend
npm install
npm run dev
```

Default frontend URL:

```text
http://localhost:5173
```

Vite proxies `/api` to `http://localhost:8080`.

## 7. Run With Docker Compose

### Full stack from the project root

This is the main challenge-aligned Docker path. It starts the frontend, backend, and PostgreSQL
with one command.

From [docker-compose.yml](docker-compose.yml):

```bash
docker compose up --build
```

Services:

- Frontend: `http://localhost:3000`
- Backend API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui`
- PostgreSQL: `localhost:5432`

Optional env overrides:

- defaults are documented in [.env.example](.env.example)
- Docker Compose will read `.env` from the project root if you add one

Stop:

```bash
docker compose down
```

Remove volumes too:

```bash
docker compose down -v
```

### Backend repo: API + database

From [tenpo-backend/docker-compose.yml](tenpo-backend/docker-compose.yml):

```bash
cd tenpo-backend
docker compose up --build
```

Services:

- Backend API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui`
- PostgreSQL: `localhost:5432`

Optional env overrides:

- defaults are documented in [tenpo-backend/.env.example](tenpo-backend/.env.example)
- Docker Compose will read `.env` from the backend repo if you add one

Stop:

```bash
docker compose down
```

Remove volumes too:

```bash
docker compose down -v
```

### Frontend repo: UI against an already running backend

The frontend repo-level compose is intentionally partial. It serves the UI on port `3000`,
builds the app with `VITE_API_BASE_URL=http://localhost:8080/api`, and uses a standalone
Nginx config that serves only the SPA.

Before starting the frontend repo compose, make sure the backend repo stack is already running
on `http://localhost:8080`.

From [tenpo-frontend/docker-compose.yml](tenpo-frontend/docker-compose.yml):

```bash
cd tenpo-frontend
docker compose up --build
```

Service:

- Frontend: `http://localhost:3000`

Optional env overrides:

- defaults are documented in [tenpo-frontend/.env.example](tenpo-frontend/.env.example)
- Docker Compose will read `.env` from the frontend repo if you add one

Stop:

```bash
docker compose down
```

## 8. API Endpoints

### List transactions

```http
GET /api/transactions
```

Optional filter:

```http
GET /api/transactions?customerName=Camila%20Torres
```

### Get a single transaction

```http
GET /api/transactions/{id}
```

### Create transaction

```http
POST /api/transactions
Content-Type: application/json
```

Example body:

```json
{
  "amountInPesos": 15000,
  "merchant": "Supermercado Lider",
  "customerName": "Camila Torres",
  "transactionDate": "2026-03-06T11:30:00"
}
```

### Update transaction

```http
PUT /api/transactions/{id}
Content-Type: application/json
```

### Delete transaction

```http
DELETE /api/transactions/{id}
```

## 9. Useful cURL Examples

Create:

```bash
curl -X POST http://localhost:8080/api/transactions \
  -H 'Content-Type: application/json' \
  -d '{
    "amountInPesos": 21000,
    "merchant": "Restaurante",
    "customerName": "Camila Torres",
    "transactionDate": "2026-03-06T19:45:00"
  }'
```

List:

```bash
curl http://localhost:8080/api/transactions
```

Filter by customer:

```bash
curl 'http://localhost:8080/api/transactions?customerName=Camila%20Torres'
```

Update:

```bash
curl -X PUT http://localhost:8080/api/transactions/1 \
  -H 'Content-Type: application/json' \
  -d '{
    "amountInPesos": 24000,
    "merchant": "Restaurante actualizado",
    "customerName": "Camila Torres",
    "transactionDate": "2026-03-06T20:15:00"
  }'
```

Delete:

```bash
curl -X DELETE http://localhost:8080/api/transactions/1
```

## 10. Testing

Backend tests cover:

- `TransactionServiceTest`
- `TransactionRepositoryTest`
- `TransactionControllerTest`
- `RateLimitFilterIntegrationTest`

Run:

```bash
cd tenpo-backend
./mvnw test
```

Frontend verification:

```bash
cd tenpo-frontend
npm run lint
npm run build
```

## 11. Deliverable Note

The challenge asks for Docker Hub publication or a `docker-compose` based way to run the project. This repository satisfies that deliverable through:

- [docker-compose.yml](docker-compose.yml) for the one-command full stack
- [tenpo-backend/docker-compose.yml](tenpo-backend/docker-compose.yml) for API + database
- [tenpo-frontend/docker-compose.yml](tenpo-frontend/docker-compose.yml) for the frontend entrypoint

The root compose is the recommended reviewer path. The repo-level compose files remain available
for separate local startup when needed.

## 12. Docker Hub Publication

The backend image is now tagged through Compose, so it is ready to publish without changing the
Docker setup.

Default local tag:

- `tenpo-backend:latest`

To publish it to Docker Hub:

1. Copy [tenpo-backend/.env.example](tenpo-backend/.env.example) to `tenpo-backend/.env`
2. Set `BACKEND_IMAGE_NAME=<your-dockerhub-user>/tenpo-backend`
3. Run `cd tenpo-backend && docker compose build backend`
4. Run `docker push <your-dockerhub-user>/tenpo-backend:latest`

If image publication is needed later, the project is already containerized and can be pushed to Docker Hub or another public registry with standard `docker build`, `docker tag`, and `docker push` commands.
