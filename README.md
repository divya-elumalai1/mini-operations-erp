# Mini Operations ERP

A production-oriented, full-stack operations ERP implementing the complete flow:

**Inventory → Work Order → Stock Check → Internal Transfer / Shortage → Customer Reservation**

Built to survive a technical interview: clean layering, real PostgreSQL transactions,
race-safe inventory mutations, backend-enforced authorization, and a 47-test integration
suite proving every mandatory scenario against a real database.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.5 (Web, Security, Data JPA, Validation), Flyway, springdoc OpenAPI, jjwt |
| Database | PostgreSQL 16 (Flyway migrations only, `ddl-auto=validate`) |
| Frontend | React 19, TypeScript (strict), Vite, React Router, TanStack Query, React Hook Form + Zod |
| Testing | JUnit 5 + MockMvc integration tests against real PostgreSQL |
| Infra | Docker Compose (PostgreSQL), env-based configuration |

## Repository layout

```
backend/     Spring Boot service (Maven)
frontend/    React SPA (Vite)
docs/        Requirements traceability, ER diagram, concurrency/transactions,
             future changes, checklist, interview prep
scripts/     dev-up.sh launcher
docker-compose.yml   Local PostgreSQL
.env.example         All configuration variables, documented
```

## Quick start

Prerequisites: Java 21+, Maven 3.9+, Node 20+, PostgreSQL 16 (native or `docker compose up -d`).

```bash
# 1. Configure
cp .env.example .env            # edit DB_USER etc. for your machine

# 2. Create databases
createdb mini_erp_dev
createdb mini_erp_test

# 3. Run everything (backend :8081, frontend :3000)
./scripts/dev-up.sh
```

Or manually:

```bash
cd backend  && SERVER_PORT=8081 DB_USER=<you> mvn spring-boot:run
cd frontend && npm install && VITE_API_PROXY_TARGET=http://localhost:8081 npm run dev -- --port 3000
```

## Environment variables

All documented in [`.env.example`](.env.example): database connection, server port,
JWT secret/expiry (override the default in any real deployment — min 32 bytes),
frontend API base URL + dev proxy target, and test-database settings (`TEST_DB_*`).

## Database setup & migrations

Schema is created exclusively by Flyway on startup:

- `V1__schema.sql` — tables, constraints, indexes, sequences
- `V2__seed.sql` — development seed data

Hibernate runs with `ddl-auto=validate`; entity drift fails at boot.
Integration tests reset data deterministically between tests.

## Seed data & development credentials

| Username | Password | Role |
|---|---|---|
| `admin` | `Admin@123` | ADMIN |
| `ops` | `Ops@123` | OPERATIONS_USER |
| `sales` | `Sales@123` | SALES_USER |

Seeded also: 3 locations, categories, items, opening stock (incl. a shortage scenario),
work order `WO-000001`, transfer `TRF-000001` awaiting dispatch. Dev credentials only.

## How to test

```bash
cd backend  && mvn test                          # unit + integration vs real PostgreSQL
cd frontend && npm run lint && npm run build     # type-check + production build
```

47 tests cover every mandatory scenario: oversell rejection, over-transfer rejection,
destination-changes-only-on-receipt, double-receipt block, role enforcement — plus a
concurrency race (8 threads → exactly 7 winners), rollback atomicity, validation,
idempotency and happy paths.

## API documentation

Swagger UI: **http://localhost:8081/swagger-ui.html** once the backend runs.
Authorize with a token from `POST /api/auth/login`.

Key endpoints:

| Method & path | Roles | Purpose |
|---|---|---|
| POST `/api/auth/login` | public | issue bearer JWT |
| GET `/api/inventory` · `/transactions` | any | stock positions / audit ledger |
| POST `/api/inventory/receipts` | ADMIN, OPS | goods receipt (idempotency key) |
| POST `/api/work-orders` | ADMIN | create work order |
| PATCH `/api/work-orders/{id}/status` | ADMIN, OPS | legal transitions only |
| GET `/api/work-orders/{id}/material-availability` | any | server-side shortage calc |
| POST `/api/transfers` · `/{id}/dispatch` · `/{id}/receive` | ADMIN, OPS | transfer lifecycle |
| POST `/api/orders` | SALES, ADMIN | order + atomic reservation |

Errors always use one envelope:
```json
{ "timestamp": "…", "status": 409, "error": "INSUFFICIENT_STOCK",
  "message": "…", "path": "/api/orders", "details": [] }
```

## Business rules enforced

- Quantities are exact `NUMERIC(14,3)`; availability is derived (`physical − reserved`), never stored.
- DB CHECKs forbid negative stock and `reserved > physical`.
- Dispatch reduces source only; destination increases exactly once at receipt
  (status-guarded UPDATE makes double receipt impossible).
- Transfers cannot ship reserved stock or exceed source availability.
- Order creation reserves every line atomically; any failing line rolls back everything.
- Duplicate receipts blocked by idempotency keys + state guards.
- Every movement writes an append-only audit ledger row in the same transaction.

## Transaction & concurrency strategy

One business operation = one transaction owned by the service layer. Stock mutations use
**atomic conditional SQL updates** (`UPDATE … WHERE available >= qty`) whose row locks
serialize writers and whose predicates make overselling impossible under READ COMMITTED.
See [`docs/CONCURRENCY.md`](docs/CONCURRENCY.md) and [`docs/TRANSACTIONS.md`](docs/TRANSACTIONS.md);
proven by `OrderReservationIT.concurrentReservationsNeverOversell`.

## Documentation index

- [`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md) — requirement → implementation → test map
- [`docs/IMPLEMENTATION-PLAN.md`](docs/IMPLEMENTATION-PLAN.md) — architecture decisions
- [`docs/ER-DIAGRAM.md`](docs/ER-DIAGRAM.md) · `er-diagram.mmd` — schema diagram
- [`docs/FUTURE-CHANGES.md`](docs/FUTURE-CHANGES.md) — damaged stock, partial receipt, cancellation, location scoping
- [`docs/FINAL-CHECKLIST.md`](docs/FINAL-CHECKLIST.md) — evaluator-style verification table
- [`docs/INTERVIEW-PREP.md`](docs/INTERVIEW-PREP.md) — answers grounded in this codebase

## Known assumptions

- Inventory grain is `(item, location)`; batches are metadata on ledger entries.
- Testcontainers requires Docker, so integration tests target a local PostgreSQL via
  env-overridable config instead (same SQL, same behavior).
- JWTs are stateless within TTL; deactivating a user takes effect at next login.
