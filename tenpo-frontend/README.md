# Tenpo Frontend

This module contains the React application for the Tenpo Full Stack Challenge.
Its responsibility is to provide a responsive transaction UI, validate input before
submission, consume the backend with Axios, and stay efficient under the challenge's
strict rate limit.

The implementation is intentionally shaped around the challenge goals:

- maintainable: small typed modules, isolated API client, clear feature boundaries
- scalable enough for the challenge: cache-first reads, local filtering, deliberate refresh
- well documented: root README, this frontend-focused README, and clear runtime config
- user-focused: create/edit/delete flows, validation, graceful API error rendering

## What This Repo Covers

- transaction listing
- local customer filtering
- create flow in a dedicated modal
- edit flow in a focused side panel
- delete action with confirmation
- frontend validation before submission
- Axios-based API communication
- React Query caching and optimistic-ish cache updates after mutations
- responsive layout for desktop and mobile

## Stack

- React 19
- TypeScript
- Vite
- Axios
- `@tanstack/react-query`
- `react-hook-form`
- `zod`
- `dayjs`
- Nginx for production container serving

## Frontend Structure

```text
src/
├── app/                      # API client
├── components/               # shared UI building blocks
├── features/transactions/    # feature-specific types, queries, schema, UI
├── App.tsx                   # screen composition and mutation orchestration
├── App.css                   # page-level styling
└── main.tsx                  # app bootstrap and query client
```

Important responsibilities:

- `src/app/api.ts`
  Axios instance and base URL configuration
- `src/features/transactions/queries.ts`
  API query/mutation helpers
- `src/features/transactions/schema.ts`
  zod validation schema and payload shaping
- `src/features/transactions/TransactionForm.tsx`
  create/edit form
- `src/features/transactions/TransactionList.tsx`
  board listing and actions
- `src/components/Modal.tsx`
  reusable modal container for create flow
- `src/App.tsx`
  screen state, React Query cache sync, error normalization, and view orchestration

## UX and State Management Choices

The challenge rate limit is very tight: `3 requests per minute per client`.

To keep the UI usable under that constraint, this frontend:

- fetches the transaction list once on load
- filters transactions locally instead of querying per keystroke
- updates React Query cache after create, update, and delete
- avoids noisy background sync behavior
- exposes manual refresh for deliberate synchronization
- normalizes backend errors into a stable UI error model

This is a deliberate engineering choice, not an omission. A constantly refetching SPA
would perform poorly under the challenge contract.

## Validation Rules

The frontend validates the same core rules as the backend:

- amount must be an integer
- amount cannot be negative
- amount must stay within integer range
- merchant is required
- Tenpista name is required
- date-time must be valid
- date-time cannot be in the future

The backend still re-validates everything, so the API remains safe even without this UI.

## Runtime Configuration

Default runtime configuration lives in:

- [`.env.example`](.env.example)
- [`docker-compose.yml`](docker-compose.yml)
- [`src/app/api.ts`](src/app/api.ts)

Relevant variables:

| Variable | Purpose | Default |
| --- | --- | --- |
| `FRONTEND_PORT` | Host port for the frontend container | `3000` |
| `VITE_API_BASE_URL` | Base URL used by the production build | `http://localhost:8080/api` |
| `FRONTEND_IMAGE_NAME` | Docker image name | `tenpo-frontend` |
| `IMAGE_TAG` | Docker image tag | `latest` |

Behavior:

- local Vite development proxies `/api` to `http://localhost:8080`
- frontend repo Docker builds with `VITE_API_BASE_URL=http://localhost:8080/api`
- root full-stack Docker build leaves `VITE_API_BASE_URL` empty so the app uses relative `/api`

## Local Development

Prerequisites:

- Node.js 22+ or recent LTS
- backend API available at `http://localhost:8080`

Install and run:

```bash
npm install
npm run dev
```

Default local URL:

```text
http://localhost:5173
```

Quality checks:

```bash
npm run lint
npm run build
```

## Docker

This repo can run by itself with:

```bash
docker compose up --build
```

What starts:

- frontend on `http://localhost:3000`

Important:

- this repo-level Docker setup expects the backend API to already be running on `http://localhost:8080`
- the container uses [`nginx.standalone.conf`](nginx.standalone.conf) so it only serves the SPA
- API calls go directly to `http://localhost:8080/api`

Stop:

```bash
docker compose down
```

If you want to override defaults, create a local `.env` file in this folder based on
[`.env.example`](.env.example).

## Manual QA Flow

1. Start the backend API.
2. Start this frontend.
3. Open `http://localhost:3000` or `http://localhost:5173`.
4. Create a transaction from the modal.
5. Confirm it appears in the list immediately.
6. Edit the same transaction from the side panel.
7. Delete it.
8. Try invalid input:
   - negative amount
   - future date
   - very large integer amount
9. Trigger a backend error or rate limit and confirm the UI shows a readable error state.

## Challenge Alignment

How this frontend maps to the challenge wording:

- React application: implemented
- modern responsive interface: implemented
- Axios fetching: implemented
- `react-query` usage: implemented
- form validation before submission: implemented
- client panel with listing: implemented
- add/edit form: implemented

Additional value beyond the minimum:

- consistent toast feedback for mutations
- modal-driven create flow and contextual edit flow
- resilient handling for non-JSON backend error responses
- cache-aware design aligned with the rate-limit restriction

## Docker Image Publication

The frontend image name and tag are externalized, so it can be published the same way as the backend.

Example:

1. Copy [`.env.example`](.env.example) to `.env`
2. Set:

```text
FRONTEND_IMAGE_NAME=<your-dockerhub-user>/tenpo-frontend
IMAGE_TAG=latest
```

3. Build:

```bash
docker compose build frontend
```

4. Push:

```bash
docker push <your-dockerhub-user>/tenpo-frontend:latest
```
