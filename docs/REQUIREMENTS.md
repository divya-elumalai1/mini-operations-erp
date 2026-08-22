# Requirements Checklist — Mini Operations ERP

Source: the placement assignment. Status legend: ✅ Implemented · 🧪 Tested · 📄 Documented.
This file is updated as phases complete; the final column set is verified in Phase 10
(`docs/FINAL-CHECKLIST.md`).

| # | Requirement | Implementation | Test | Planned |
|---|---|---|---|---|
| F1 | Business flow inventory→WO→shortage→transfer→order→reservation preserved | `InventoryService`, `WorkOrderService`, `TransferService`, `OrderService` | `OrderFlowIntegrationTest`, `TransferFlowIT` | Planned |
| F2 | Login screen (frontend) | `frontend/src/features/auth/LoginPage.tsx` | `AuthControllerIT` (backend), manual UI | Planned |
| F3 | Inventory screen w/ stock + receipts + ledger | `InventoryPage.tsx`, `/api/inventory*` | `InventoryIT` | Planned |
| F4 | Work Orders screen (create/list/status/shortage) | `WorkOrdersPage.tsx`, `/api/work-orders*` | `WorkOrderIT` | Planned |
| F5 | Internal Transfers screen (create/dispatch/receive) | `TransfersPage.tsx`, `/api/transfers*` | `TransferIT` | Planned |
| F6 | Customer Orders screen (create w/ reservation) | `OrdersPage.tsx`, `/api/orders` | `OrderReservationIT` | Planned |
| F7 | Work order statuses ASSIGNED/IN_PROGRESS/COMPLETED, legal transitions only | `WorkOrderStatus` + `WorkOrderService.changeStatus` | `WorkOrderIT.invalidTransitionsRejected` | Planned |
| F8 | Backend shortage calculation (required 100 / available 70 ⇒ shortage 30) | `WorkOrderService.getMaterialAvailability` | `WorkOrderIT.shortageCalculatedByBackend` | Planned |
| F9 | Transfer statuses REQUESTED/DISPATCHED/RECEIVED, valid transitions only | `TransferService.dispatch/receive` guarded updates | `TransferIT` | Planned |
| F10 | Dispatch: source ↓, destination MUST NOT change | `TransferService.dispatchTransaction` | `TransferIT.destinationUnchangedAfterDispatch` | Planned |
| F11 | Receive: destination ↑, exactly once | atomic status-guarded receive | `TransferIT.duplicateReceiptRejected` | Planned |
| F12 | Cannot transfer more than available at source (10 vs 11 rejected) | conditional UPDATE predicate | `TransferIT.dispatchInsufficientStock` | Planned |
| F13 | Customer order creation reserves stock atomically; reserved ↑, available ↓ | `OrderService.createOrderWithReservation` | `OrderReservationIT` | Planned |
| F14 | Never reserve beyond available (10 vs 11 rejected, unchanged) | conditional UPDATE predicate | `OrderReservationIT.cannotReserveMoreThanAvailable` | Planned |
| F15 | Two simultaneous reservations of last stock ⇒ exactly one succeeds | atomic conditional update + row lock | `ConcurrentReservationIT` | Planned |
| F16 | Roles ADMIN / OPERATIONS_USER / SALES_USER enforced **backend-side** | Spring Security `@PreAuthorize` per endpoint | `AuthorizationIT` | Planned |
| F17 | 401 unauthenticated / 403 unauthorized semantics | `SecurityConfig` + `GlobalExceptionHandler` | `AuthControllerIT`, `AuthorizationIT` | Planned |
| T1 | Physical/Reserved/Available distinction; available = physical − reserved | `inventory` table + DTO derivation | unit+integration asserts | Planned |
| T2 | DB rejects physical < 0, reserved < 0, reserved > physical | CHECK constraints in V1 migration | `SchemaConstraintIT` | Planned |
| T3 | No floating point quantities — NUMERIC(14,3) everywhere | V1 migration | schema assertion | Planned |
| T4 | Duplicate inventory transactions prevented | `idempotency_key UNIQUE` on ledger + guards | `InventoryIT.duplicateIdempotencyKeyRejected` | Planned |
| T5 | Auditability: every stock movement recorded (who/what/where/ref/delta) | `inventory_transactions` append-only ledger | asserted in all flow ITs | Planned |
| T6 | Centralized inventory mutation logic (no bypass) | single `InventoryLedger` component used by all services | code structure review | Planned |
| N1 | Normalized schema, FKs, NOT NULLs, unique business ids | V1__schema.sql | `SchemaConstraintIT` | Planned |
| N2 | Flyway migrations only; no auto-generated schema | `ddl-auto=validate` + V1/V2 migrations | boot-time validation | Planned |
| A1 | Passwords BCrypt-hashed, never plaintext | `SecurityConfig.passwordEncoder` | seed hash format assert | Planned |
| A2 | JWT stateless auth on every protected request | `JwtAuthenticationFilter` | `AuthControllerIT` | Planned |
| V1 | Bean Validation at API boundary (required fields, qty > 0, etc.) | jakarta.validation on DTOs | `ValidationErrorIT` | Planned |
| V2 | Business-level validation (source≠destination, transitions, availability) | domain exceptions from services | respective ITs | Planned |
| E1 | Consistent error envelope {timestamp,status,error,message,path,details} | `GlobalExceptionHandler` | `ErrorContractIT` | Planned |
| E2 | Handles: validation/authn/authz/not-found/duplicate/insufficient stock/invalid transition/db constraint/unexpected — no stack traces leaked | `GlobalExceptionHandler` | `ErrorContractIT` | Planned |
| D1 | OpenAPI/Swagger documents auth, schemas, errors | springdoc + annotations | manual + smoke test | Planned |
| S1 | Seed data: 3 users, locations, categories, items, stock, example WO + transfer | `V2__seed.sql` | boots in dev profile | Planned |
| C1 | docker-compose for local PostgreSQL | `docker-compose.yml` | config validated (no Docker host here) | Planned |
| C2 | `.env.example` documenting all env vars | `.env.example` | review | Planned |
| Q1 | Mandatory Test 1: reserve > available rejected, unchanged | `OrderReservationIT` | pass | Planned |
| Q2 | Mandatory Test 2: transfer > available rejected, unchanged | `TransferIT` | pass | Planned |
| Q3 | Mandatory Test 3: dest changes only after receipt | `TransferIT` | pass | Planned |
| Q4 | Mandatory Test 4: double receipt rejected | `TransferIT` | pass | Planned |
| Q5 | Mandatory Test 5: unauthorized role → 403 | `AuthorizationIT` | pass | Planned |
| Q6 | Additional: authn, validation, invalid transitions, shortage calc, duplicates, concurrent reservations, rollback, happy paths | see test tree | pass | Planned |
| DOC1 | README covering setup/run/test/API/roles/rules/docker | `README.md` | review | Planned |
| DOC2 | ER diagram matching implementation | `docs/ER-DIAGRAM.md`, `docs/er-diagram.mmd` | review vs DDL | Planned |
| DOC3 | CONCURRENCY.md incl. race-prevention explanation | `docs/CONCURRENCY.md` | review | Planned |
| DOC4 | TRANSACTIONS.md boundaries | `docs/TRANSACTIONS.md` | review | Planned |
| DOC5 | FUTURE-CHANGES.md (damaged stock, partial receipt, cancellation, location scoping) | `docs/FUTURE-CHANGES.md` | review | Planned |
| DOC6 | Interview prep doc | removed per request | — |
| G1 | Progressive git history (no single giant commit) | git log | review | Planned |
