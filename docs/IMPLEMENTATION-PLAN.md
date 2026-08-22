# Mini Operations ERP — Implementation Plan

Status: living document, updated as implementation lands.

## 1. Source of truth

The placement assignment (provided in full in the task brief) is the specification.
Every requirement is tracked with an implementation/test mapping in `docs/REQUIREMENTS.md`.

## 2. Repository layout

```
backend/    Spring Boot service (Java 21 target, Maven)
frontend/   React + TypeScript SPA (Vite)
docs/       All design / requirements / QA documents
scripts/    Local setup helper scripts (non-Docker PostgreSQL bootstrap)
docker-compose.yml  PostgreSQL (+ optional pgAdmin) for local development
.env.example
```

## 3. Architecture

Backend: classic layered Spring Boot monolith — deliberately simple, no microservices,
no message broker. The domain is small; clarity beats distribution.

```
controller  → thin HTTP adapters: map request DTOs, call services, return response DTOs
service     → all business rules and transaction boundaries
repository  → Spring Data JPA + targeted native atomic UPDATE queries
domain      → JPA entities and enums
dto         → request/response records (API boundary never leaks entities)
mapper      → entity ↔ DTO mapping
security    → JWT issuance/validation, filter, user details
exception   → domain exceptions + centralized @RestControllerAdvice
config      → security, OpenAPI, bean wiring
```

Frontend: feature-sliced React app. Server state via TanStack Query, forms via
React Hook Form + Zod, routing via React Router, HTTP via a small typed Axios client.
Role-aware UI hides disallowed actions; **the backend remains the authorization authority**.

## 4. Modules

| Module | Contents |
|---|---|
| Auth | login (JWT), password hashing (BCrypt), role model |
| Master data | items, categories, locations |
| Inventory | stock rows, goods receipts, availability, transaction ledger |
| Work orders | lifecycle + material shortage calculation |
| Internal transfers | REQUESTED → DISPATCHED → RECEIVED lifecycle |
| Customer orders | order + line-item reservation |

## 5. Database schema (PostgreSQL 16, Flyway-managed)

Full DDL lives in `backend/src/main/resources/db/migration/V1__schema.sql`.
Diagram: `docs/ER-DIAGRAM.md` / `docs/er-diagram.mmd`.

- `users(id, username UNIQUE, password_hash, full_name, role CHECK IN (...), active)`
- `locations(id, code UNIQUE, name, active)` — stock-holding places
- `categories(id, name UNIQUE)`, `items(id, sku UNIQUE, category_id FK, unit_of_measure)`
- `batches(id, item_id FK, batch_code, expires_on, UNIQUE(item_id, batch_code))` — lot metadata
- `inventory(id, item_id, location_id, physical_qty, reserved_qty, version, updated_at,
  UNIQUE(item_id, location_id),
  CHECK physical_qty >= 0 AND reserved_qty >= 0 AND reserved_qty <= physical_qty)`
- `inventory_transactions(...)` — append-only audit ledger of every stock movement
  (type, reference type/id, physical/reserved deltas, idempotency_key UNIQUE, performed_by)
- `work_orders(wo_number UNIQUE, item_id, location_id, required_quantity > 0,
  assigned_user_id, status ASSIGNED|IN_PROGRESS|COMPLETED, created_by)`
- `internal_transfers(transfer_number UNIQUE, source_location_id, destination_location_id,
  CHECK source <> destination, item_id, quantity > 0,
  status REQUESTED|DISPATCHED|RECEIVED, dispatched_by/at, received_by/at)`
- `customer_orders(order_number UNIQUE, customer_name, status CREATED|CANCELLED, created_by)`
- `customer_order_items(order_id FK CASCADE, item_id, location_id, quantity > 0)`

Key decisions:

1. **Quantities are `NUMERIC(14,3)`** — never floating point.
2. **Inventory grain is (item, location).** Batches exist as lot metadata referenced by
   inventory transactions. The business flows in scope (reserve/transfer/order) do not
   require per-batch allocation; moving to batch-grain stock means adding a nullable
   `batch_id` to `inventory` and changing its unique key — isolated change (see
   `docs/FUTURE-CHANGES.md`).
3. **Available quantity is derived**: `physical - reserved`. It is never stored.
4. **Database-level CHECK constraints** back every application invariant so even a bug
   outside the service layer cannot corrupt stock.
5. Business identifiers (`WO-000123`, `TRF-000042`, `SO-000007`) come from dedicated
   PostgreSQL sequences — race-free numbering.

## 6. API design

Base path `/api`. JSON only. Errors use one consistent envelope:

```json
{ "timestamp": "...", "status": 400, "error": "VALIDATION_ERROR", "message": "...",
  "path": "/api/orders", "details": [ { "field": "quantity", "message": "must be > 0" } ] }
```

| Method & path | Role(s) | Purpose |
|---|---|---|
| POST `/api/auth/login` | public | issue JWT |
| GET `/api/inventory` | any authenticated | paginated stock w/ filters |
| GET `/api/inventory/transactions` | any authenticated | audit ledger |
| POST `/api/inventory/receipts` | ADMIN, OPERATIONS_USER | goods receipt (+ ledger row) |
| GET/POST `/api/items`, `/api/locations`, GET `/api/categories` | read: any authenticated; create: ADMIN | master data |
| POST `/api/work-orders` | ADMIN | create work order |
| GET `/api/work-orders`, `/api/work-orders/{id}` | any authenticated | list/detail |
| PATCH `/api/work-orders/{id}/status` | ADMIN, OPERATIONS_USER | legal transition only |
| GET `/api/work-orders/{id}/material-availability` | any authenticated | shortage calculation |
| POST `/api/transfers` | ADMIN, OPERATIONS_USER | create transfer (REQUESTED) |
| POST `/api/transfers/{id}/dispatch` | ADMIN, OPERATIONS_USER | source ↓, dest unchanged |
| POST `/api/transfers/{id}/receive` | ADMIN, OPERATIONS_USER | dest ↑, once only |
| GET `/api/transfers`, `/api/transfers/{id}` | any authenticated | list/detail |
| POST `/api/orders` | SALES_USER, ADMIN | order + atomic reservation |
| GET `/api/orders`, `/api/orders/{id}` | any authenticated | list/detail |

OpenAPI UI at `/swagger-ui.html`.

## 7. Authentication approach

Stateless JWT (jjwt), HS256, secret from `JWT_SECRET` env (dev default documented in
`.env.example`; production must override). BCrypt for password storage. A single filter
authenticates every request; method security (`@PreAuthorize`) authorizes per endpoint.
401 = unauthenticated/invalid token; 403 = authenticated but insufficient role.

## 8. Authorization matrix

| Capability | ADMIN | OPERATIONS_USER | SALES_USER |
|---|---|---|---|
| Login, view own profile | ✓ | ✓ | ✓ |
| Read inventory / master data / orders / transfers / work orders | ✓ | ✓ | ✓ |
| Goods receipt (stock in) | ✓ | ✓ | ✗ |
| Create/dispatch/receive internal transfers | ✓ | ✓ | ✗ |
| Create work orders | ✓ | ✗ | ✗ |
| Progress/complete work orders | ✓ | ✓ | ✗ |
| Create customer orders (reserve stock) | ✓ | ✗ | ✓ |

ADMIN ⊇ everything. Frontend may hide controls per role but this is UX only.

## 9. Transaction strategy

One service method = one transaction (`@Transactional`). Multi-record mutations
(goods receipt, dispatch, receive, order+reservation) are atomic: any failure rolls back
every write including ledger rows. Details: `docs/TRANSACTIONS.md`.

## 10. Concurrency strategy

Chosen mechanism: **atomic conditional SQL updates** guarded by quantity predicates,
executed inside transactions on PostgreSQL (READ COMMITTED):

```sql
-- reserve q units only if available
UPDATE inventory SET reserved_qty = reserved_qty + :q
WHERE item_id=:i AND location_id=:l AND (physical_qty - reserved_qty) >= :q
```

Zero rows affected ⇒ insufficient stock ⇒ domain exception ⇒ rollback.
Row locks taken by the UPDATE serialize competing writers; the predicate makes
overselling impossible regardless of interleaving. Destination-side upserts use
`INSERT ... ON CONFLICT DO UPDATE` to stay atomic when no inventory row exists yet.
Optimistic `@Version` columns act as a secondary safety net. Rationale and race analysis:
`docs/CONCURRENCY.md`.

## 11. Testing strategy

- Unit tests: shortage calculator, state-transition rules, JWT service.
- Integration tests: `@SpringBootTest` against a **real PostgreSQL** database
  (`mini_erp_test`), Flyway clean+migrate for isolation. Testcontainers requires Docker;
  this environment has none, so tests run against locally installed PostgreSQL 16 via
  env-overridable datasource config. Docker users can point the same config at the
  docker-compose database unchanged.
- Concurrency tests: real thread pools issuing parallel reservations through the actual
  service layer against the real DB — asserting exactly-one-winner semantics.
- HTTP-layer tests (MockMvc): authn/authz status codes, validation errors.

Mandatory scenarios covered: reserve-beyond-available, transfer-beyond-available,
dispatch/receive inventory movement, double receipt rejection, unauthorized role
rejection, plus concurrency, rollback, validation, happy paths.

## 12. Implementation phases

1. Docs & plan (this file) → git scaffold.
2. Backend foundation: build, migrations, entities, repos, security, error handling.
3. Inventory domain + ledger + tests.
4. Work orders + shortage tests.
5. Transfers + lifecycle/duplicate-receipt tests.
6. Customer orders + concurrency-safe reservation + race tests.
7. Frontend: 5 screens wired to live APIs.
8. Documentation completion.
9. Full QA (tests, lint, typecheck, builds, smoke).
10. Final evaluator-style review (`docs/FINAL-CHECKLIST.md`).

Each phase ends green (compile + relevant tests) and is committed separately.
