# Tenpo Frontend

React 19 + TypeScript SPA for the Tenpo Full Stack Challenge.
Provides a responsive transaction management UI, validates input before submission,
consumes the backend with Axios, and stays efficient under the challenge's strict
per-client rate limit.

---

## Table of Contents

1. [Stack](#1-stack)
2. [Module Map and Component Tree](#2-module-map-and-component-tree)
3. [State and Data Flow](#3-state-and-data-flow)
4. [React Query Cache Strategy](#4-react-query-cache-strategy)
5. [Validation Rules](#5-validation-rules)
6. [DRY Analysis](#6-dry-analysis)
7. [API Key Authentication](#7-api-key-authentication)
8. [Performance Notes](#7b-performance-notes)
9. [Runtime Configuration](#8-runtime-configuration)
9. [Local Development](#9-local-development)
10. [Docker](#10-docker)
11. [Manual QA Flow](#11-manual-qa-flow)
12. [Challenge Alignment](#12-challenge-alignment)
13. [Docker Image Publication](#13-docker-image-publication)

---

## 1. Stack

| Technology | Version | Purpose |
|---|---|---|
| React | 19 | UI framework |
| TypeScript | 5.x | Type safety |
| Vite | 6.x | Dev server + bundler |
| Axios | — | HTTP client |
| `@tanstack/react-query` | 5.x | Server state cache |
| `react-hook-form` | — | Controlled forms |
| `zod` | — | Schema validation |
| `dayjs` | — | Date formatting |
| `@fontsource/*` | — | Self-hosted fonts (offline-safe) |
| Nginx | alpine | Production static file serving |

---

## 2. Module Map and Component Tree

### Directory structure

```
src/
├── main.tsx                           ← Bootstrap: QueryClient, StrictMode, mount
├── App.tsx                            ← Root: state, mutations, layout orchestration
├── App.css                            ← Global page-level styles
├── index.css                          ← CSS reset + design tokens (variables)
│
├── app/
│   └── api.ts                         ← Shared Axios instance, base URL resolution
│
├── components/
│   └── Modal.tsx                      ← Generic accessible modal (createPortal + ARIA)
│
├── features/
│   └── transactions/
│       ├── types.ts                   ← TypeScript interfaces: Transaction, ApiError, …
│       ├── schema.ts                  ← Zod schema + getInitialFormValues + toPayload
│       ├── queries.ts                 ← transactionKeys, query options, mutation fns
│       ├── TransactionList.tsx        ← Card list renderer (stringToHue, getInitials)
│       └── TransactionForm.tsx        ← Controlled form (create + edit dual mode)
│
└── lib/
    └── formatters.ts                  ← formatCLP() — shared CLP currency formatter
```

### Component tree

```
App (root)
│
├── [hero section]
│   ├── "New transaction" button → handleCreateMode()
│   └── "Refresh" button → refetch()
│
├── [stats grid]
│   ├── Visible movements count
│   ├── Visible amount (formatCLP)
│   ├── Unique Tenpistas count
│   └── Latest movement date + customer
│
├── [workspace]
│   ├── [panel--list]
│   │   ├── customer filter input
│   │   ├── list error banner (on fetch failure)
│   │   ├── [skeleton-list] (while isLoading)
│   │   └── TransactionList
│   │       └── [transaction-card ×N]
│   │           ├── avatar badge (initials, hsl color from merchant hash)
│   │           ├── merchant + customer + id pill
│   │           ├── amount (formatCLP) + date (dayjs)
│   │           ├── Edit button → onEdit(transaction)
│   │           └── Delete button → onDelete(transaction)
│   │
│   └── [workspace__side]
│       ├── [panel--form] (when activeTransaction is set)
│       │   ├── TransactionForm (edit mode)
│       │   └── note-card (cache strategy explanation)
│       └── [panel--editor-empty] (when no activeTransaction)
│           ├── "Pick a record or start fresh" copy
│           ├── "New transaction" button
│           └── note-card (modal UX explanation)
│
├── Modal (createPortal → document.body)
│   └── [create-flow]
│       ├── hero + rule pills
│       └── TransactionForm (create mode, variant="modal")
│
└── [toast] (ephemeral success notification)
```

---

## 3. State and Data Flow

### App state

```
State variable          Type                    Description
─────────────────────── ──────────────────────  ─────────────────────────────────────
transactions            Transaction[]           React Query cache (fetch once on load)
activeTransaction       Transaction | null      Card currently open in the side panel
isCreateModalOpen       boolean                 Controls Modal visibility
serverError             ApiError | null         Last failed mutation error → fed to form
draftCustomerFilter     string                  Raw input value (high-priority state)
deferredCustomerFilter  string                  Deferred copy (low-priority, for filtering)
toast                   string | null           Ephemeral success message (3 s auto-dismiss)
isPanelPending          boolean                 True while useTransition batch is processing
```

### Derived values (zero extra requests)

```
visibleTransactions  = transactions.filter(tx => tx.customerName includes deferredFilter)
totalAmount          = sum of visibleTransactions.amountInPesos
uniqueCustomers      = count of distinct customerName (lowercase) in visibleTransactions
latestTransaction    = visibleTransactions[0]  (list is sorted newest-first)
```

### Mutation flow

```
User submits form
  │
  ├─ activeTransaction is set → updateMutation.mutateAsync({id, payload})
  └─ activeTransaction is null → createMutation.mutateAsync(payload)
          │
          ▼
  API call (POST or PUT /api/transactions[/{id}])
          │
          ├─ onSuccess: queryClient.setQueryData() → cache updated, no refetch
          │             showToast("Transaction …")
          │             setActiveTransaction(result)
          │
          └─ onError:   extractApiError() → setServerError()
                        TransactionForm displays inline field errors or banner
```

---

## 4. React Query Cache Strategy

The challenge rate limit is `3 requests / minute / client`.
A default SPA (refetch on focus, on reconnect, after every mutation) would exhaust the quota immediately. Every QueryClient option is a deliberate trade-off:

| Option | Value | Reason |
|---|---|---|
| `staleTime` | `60 000` ms | Data is fresh for 1 min → no background refetch in that window |
| `gcTime` | `600 000` ms | Unused cache survives 10 min → navigation back doesn't re-fetch |
| `retry` | `0` | Each retry consumes 1 of 3 req/min; errors surface immediately |
| `refetchOnWindowFocus` | `false` | Alt-tabbing back would fire a request every time |
| `refetchOnReconnect` | `false` | Network reconnect would fire a request automatically |

After mutations: `queryClient.setQueryData()` updates the cache in-place → no network request.
Manual sync: the "Refresh" button calls `refetch()` for deliberate synchronization.

---

## 5. Validation Rules

The Zod schema in `schema.ts` enforces the same rules as the backend:

| Field | Rule | Error message |
|---|---|---|
| `amountInPesos` | Required integer | "Amount must be an integer." |
| `amountInPesos` | ≥ 0 | "Amount cannot be negative." |
| `amountInPesos` | ≤ 2 147 483 647 | "Amount must stay below 2,147,483,647." |
| `merchant` | Required (non-empty after trim) | "Merchant is required." |
| `merchant` | ≤ 160 characters | "Merchant must stay under 160 characters." |
| `customerName` | Required (non-empty after trim) | "Tenpista name is required." |
| `customerName` | ≤ 120 characters | "Tenpista name must stay under 120 characters." |
| `transactionDate` | Valid datetime | "Use a valid date and time." |
| `transactionDate` | Not in the future | "Transaction date cannot be in the future." |

The `datetime-local` input also sets `max={new Date().toISOString().slice(0,16)}` for browser-native future-date prevention (accessible, no JS required).

The backend re-validates everything independently — the API is safe even without the UI.

---

## 6. DRY Analysis

### `lib/formatters.ts` — shared CLP currency formatter

**Problem**: `Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 })` was instantiated independently in both `App.tsx` and `TransactionList.tsx`. A change to locale or options would require editing two files and risk divergence.

**Fix**: extracted into `src/lib/formatters.ts` as `formatCLP(amount: number): string`. Both files now import the shared function. The `Intl.NumberFormat` object is a module-level constant — created once at import time, not on every render.

### `transactionKeys` factory (`queries.ts`)

All React Query cache operations reference `transactionKeys.all`. Changing the cache key requires editing one line.

### `toPayload()` and `getInitialFormValues()` (`schema.ts`)

Defined once, called from both `TransactionForm` (initial state, reset) and `App.tsx` (submit callback). Whitespace normalization (`.trim().replace(/\s+/g, ' ')`) mirrors the backend `sanitizeText()` in a single place.

### `normalizeApiError()` (`App.tsx`)

All three mutation `onError` handlers call `extractApiError()` → `normalizeApiError()`. Error coercion logic is not repeated.

---

## 7. API Key Authentication

The frontend reads the API key from the `VITE_API_KEY` environment variable (falls back to `tenpo-dev-key`). The Axios instance in `src/app/api.ts` attaches an `X-API-Key` header to **every request** automatically via a request interceptor.

```
VITE_API_KEY is set (e.g. in .env)
  → use that value as X-API-Key header

VITE_API_KEY is not set
  → fall back to "tenpo-dev-key"
```

The `.env.example` file includes `VITE_API_KEY=tenpo-dev-key`. Set this variable before running in an environment where the backend uses a different key.

---

## 7b. Performance Notes

| Technique | Where | Effect |
|---|---|---|
| Fetch once, filter locally | `App.tsx` | Zero extra requests for customer filtering |
| `useDeferredValue` | `App.tsx` | Filter input stays responsive while list re-renders |
| `useTransition` for panel state | `App.tsx` | Panel open/close doesn't block urgent renders |
| Optimistic cache updates | mutation `onSuccess` | `setQueryData()` instead of `refetch()` |
| Module-level `Intl.NumberFormat` | `lib/formatters.ts` | Created once at import, reused across all renders |
| CSS animation stagger via `--stagger` | `TransactionList.tsx` | Stagger computed in CSS from a custom property — no JS animation loop |
| Merchant hue is computed, not stored | `TransactionList.tsx` | `stringToHue()` runs on render; no extra API field needed |
| Self-hosted fonts (`@fontsource`) | `main.tsx` | Works offline and in Docker without CDN |
| `react-hook-form` | `TransactionForm.tsx` | Uncontrolled inputs with minimal re-renders |
| `useMemo` for derived values | `App.tsx` | `visibleTransactions`, `totalAmount`, `uniqueCustomers` recomputed only when their dependencies change |
| `useCallback` for event handlers | `App.tsx` | Stable function references prevent unnecessary re-renders of child components |
| `React.memo` on list and form | `TransactionList`, `TransactionForm` | Components skip re-render when their props are unchanged |
| `manualChunks` in `vite.config.ts` | Build output | Vendor libs split into `vendor-react`, `vendor-query`, `vendor-utils`, `vendor-forms` — better browser cache utilization across deploys |

---

## 8. Runtime Configuration

| Variable | Purpose | Default |
|---|---|---|
| `FRONTEND_PORT` | Host port for the container | `3000` |
| `VITE_API_BASE_URL` | Base URL for the production build | `""` (uses relative `/api`) |
| `VITE_API_KEY` | API key sent as `X-API-Key` on every request | `tenpo-dev-key` |
| `FRONTEND_IMAGE_NAME` | Docker image name | `tenpo-frontend` |
| `IMAGE_TAG` | Docker image tag | `latest` |

### Base URL logic (`src/app/api.ts`)

```
VITE_API_BASE_URL is set (repo-level Docker, e.g. http://localhost:8080/api)
  → use that value

VITE_API_BASE_URL is empty (root full-stack Docker + local Vite dev)
  → use relative /api
  → Nginx proxies /api/* → http://backend:8080  (full-stack Docker)
  → Vite dev server proxies /api/* → http://localhost:8080  (local dev)
```

Full list: [`.env.example`](.env.example)

---

## 9. Local Development

Prerequisites: Node.js 20+ (or recent LTS), backend running on `http://localhost:8080`.

```bash
npm install
npm run dev       # → http://localhost:5173
```

Quality checks:

```bash
npm run lint      # ESLint
npm run build     # tsc -b + vite build (type-check + bundle)
```

---

## 10. Docker

Frontend only (requires backend at `http://localhost:8080`):

```bash
docker compose up --build
```

| Service | URL |
|---|---|
| Frontend | http://localhost:3000 |

Uses [`nginx.standalone.conf`](nginx.standalone.conf) — serves the SPA and proxies `/api` directly to `http://localhost:8080/api`.

Stop: `docker compose down`

For the full stack (frontend + backend + database), use the root [`docker-compose.yml`](../docker-compose.yml).

---

## 11. Manual QA Flow

1. Start the backend (`cd tenpo-backend && docker compose up --build`).
2. Start this frontend (`docker compose up --build` or `npm run dev`).
3. Open `http://localhost:3000` (or `http://localhost:5173` for local dev).
4. Create a transaction from the "New transaction" modal.
5. Confirm it appears in the list immediately (cache update, no refetch).
6. Click Edit → modify a field → Save changes.
7. Delete the transaction → confirm it disappears from the list.
8. Test validation:
   - negative amount → inline error below field
   - future date → inline error below field
   - empty merchant → inline error below field
9. Trigger a backend 429 by clicking Refresh more than 3 times in 60 s → confirm the error banner appears in the panel.
10. Resize the browser window — confirm the layout adapts for mobile.

---

## 12. Challenge Alignment

| Challenge requirement | Implementation |
|---|---|
| React application | Vite + React 19 + TypeScript |
| Modern responsive interface | CSS Grid + custom properties, mobile breakpoints |
| Axios fetching | `src/app/api.ts` shared Axios instance |
| `react-query` usage | `@tanstack/react-query` v5, `useQuery`, `useMutation` |
| Form validation before submission | `zod` schema + `react-hook-form` |
| Client panel with listing | `TransactionList` with filter and stats |
| Add/edit form | `TransactionForm` (dual mode: create in modal, edit in side panel) |

Additional value:

- Toast notifications for every mutation outcome
- Server field errors mapped back into form state (inline display)
- Resilient error normalizer handles partial/invalid API error shapes
- `useDeferredValue` + `useTransition` for smooth filtering under load
- Merchant color identity (hue derived from name hash — consistent across re-renders)

---

## 13. Docker Image Publication

```bash
# 1. Copy and configure env
cp .env.example .env
# Set: FRONTEND_IMAGE_NAME=<your-user>/tenpo-frontend

# 2. Build
docker compose build frontend

# 3. Push
docker push <your-user>/tenpo-frontend:latest
```
