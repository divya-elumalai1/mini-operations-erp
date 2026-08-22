# Final Checklist — Evaluator Simulation

Every claim below was verified against the working system: 47/47 backend tests green
against real PostgreSQL; frontend type-check, lint and production build pass; live
smoke test over HTTP (login, JWT, role rejection, Swagger, UI proxy).

## Assignment-mandated functionality

| Requirement | Implementation | Test evidence | Status |
|---|---|---|---|
| Flow inventory→WO→shortage→transfer→order→reservation | service chain, shared `InventoryLedger` | cross-module flow ITs | ✅ |
| Login screen + roles | `LoginPage.tsx`, `/api/auth/login` | `AuthAuthorizationIT` | ✅ |
| Inventory screen (receipts + audit ledger) | `InventoryPage.tsx` | `InventoryIT` | ✅ |
| Work Orders screen (create/status/shortage) | `WorkOrdersPage.tsx` | `WorkOrderIT` | ✅ |
| Internal Transfers screen (create/dispatch/receive) | `TransfersPage.tsx` | `TransferIT` | ✅ |
| Customer Orders screen (+ reservation) | `OrdersPage.tsx` | `OrderReservationIT` | ✅ |
| WO legal transitions only | state machine in service | illegal-transition ITs | ✅ |
| Backend shortage calc | `WorkOrderService.getMaterialAvailability` | shortage ITs | ✅ |
| Transfer lifecycle + guards | guarded transitions | `TransferIT` | ✅ |
| Dispatch: source ↓, dest unchanged | `TransferService.dispatch` | destination-on-receipt IT | ✅ |
| Receive: dest ↑ exactly once | status-guarded UPDATE | double-receipt IT | ✅ |
| Cannot transfer > available | conditional UPDATE predicate | dispatch-insufficient IT | ✅ |
| Atomic reservation on order | single-tx order+reserve | reservation ITs | ✅ |
| Never reserve beyond available | conditional UPDATE predicate | oversell ITs | ✅ |
| Concurrent reservations: exactly-one-winner | atomic predicate + row lock | race IT (8 threads) | ✅ |
| Backend-enforced roles | `@PreAuthorize` per endpoint | 12 authz cases | ✅ |
| 401 / 403 semantics | security entry points | `AuthAuthorizationIT` | ✅ |

## Technical requirements

| Requirement | Evidence | Status |
|---|---|---|
| Physical/Reserved/Available distinction | schema + derived availability asserted everywhere | ✅ |
| No floating-point quantities | NUMERIC(14,3), BigDecimal | ✅ |
| DB CHECKs (physical ≥ 0, reserved ≤ physical) | V1 migration backstop | ✅ |
| Normalized schema, FKs, unique business ids | V1 migration | ✅ |
| Flyway-only schema; validate mode | boot-time validation | ✅ |
| BCrypt password storage | encoder + seed hashes | ✅ |
| Stateless JWT on protected requests | filter + SecurityConfig | ✅ |
| Bean Validation + field errors | jakarta.validation + details[] | ✅ |
| Centralized errors, no stack traces leaked | `GlobalExceptionHandler` | ✅ |
| Idempotency for duplicate transactions | unique idempotency_key + guard updates | ✅ |
| Append-only audit ledger per movement | `inventory_transactions` in-tx | ✅ |
| Single inventory mutation gateway | `InventoryLedger` (MANDATORY propagation) | ✅ |
| OpenAPI/Swagger documents auth + endpoints | springdoc + OpenAPI contract test | ✅ |
| Pagination + filtering on lists | PageResponse envelope | ✅ |
| Deterministic lock ordering | merged lines sorted before reserve | ✅ |

## Mandatory test scenarios (all passing)

| # | Scenario | Test |
|---|---|---|
| 1 | Reserve 11 of 10 → rejected, unchanged | oversell ITs |
| 2 | Transfer 11 of 10 → dispatch rejected, source unchanged | dispatch-insufficient IT |
| 3 | Destination changes only after receipt | destination-on-receipt IT |
| 4 | Same transfer received twice → rejected | double-receipt IT |
| 5 | Unauthorized role → 403 | `AuthAuthorizationIT` |
| + | Concurrency race, rollback, validation, dupes, happy paths | 47 tests total |

## Deliverables

| Deliverable | Location | Status |
|---|---|---|
| README (setup→run→test→rules) | `README.md` | ✅ |
| ER diagram matching DDL | `docs/ER-DIAGRAM.md`, `er-diagram.mmd` | ✅ |
| Requirements traceability | `docs/REQUIREMENTS.md` | ✅ |
| Implementation plan | `docs/IMPLEMENTATION-PLAN.md` | ✅ |
| Concurrency rationale | `docs/CONCURRENCY.md` | ✅ |
| Transaction boundaries | `docs/TRANSACTIONS.md` | ✅ |
| Future-change readiness | `docs/FUTURE-CHANGES.md` | ✅ |
| Docker compose (PostgreSQL) | `docker-compose.yml` | 📄 config provided (not executed here — native PG used) |
| `.env.example` | `.env.example` | ✅ |
| Progressive git history | logical commits per phase | ✅ |

## Honest limitations

- docker-compose.yml is provided but was not executed in this environment (no Docker
  daemon); PostgreSQL ran natively instead.
- Testcontainers replaced by local-PostgreSQL integration testing for the same reason;
  swapping later is a test-scope-only change.
- Work-order assignee dropdown lists the seeded ops user rather than calling a
  user-list API (user management intentionally out of scope).
