# Future Change Readiness

The assignment states candidates may receive unexpected business-rule changes. The
architecture anticipates four named scenarios. None are implemented; each maps onto
existing seams with a contained change.

## 1. Damaged stock reduces available stock

Current seam: `inventory(physical_qty, reserved_qty)` + typed ledger
(`InventoryTxType`).

Change: add `damaged_qty NUMERIC(14,3) NOT NULL DEFAULT 0` with CHECK
`(0 <= damaged_qty) AND (damaged_qty <= physical_qty)`. Availability becomes
`physical - reserved - damaged`, computed in `AvailableQuantityCalculator`
(single derivation point). New ledger types `DAMAGE_WRITE_OFF`, `DAMAGE_RESTORE`.
New endpoint `POST /api/inventory/damage-records` (OPS/ADMIN) using the same guarded-
update pattern (`WHERE physical_qty - reserved_qty - damaged_qty >= :q`). DB CHECKs
extend accordingly. No flow logic changes — reservation/dispatch predicates simply
reference the calculator's SQL fragment.

## 2. Partial transfer receipt

Current seam: transfer status machine + `receive()` upsert.

Change: add `received_qty NUMERIC(14,3) NOT NULL DEFAULT 0` to `internal_transfers`,
allow receipt calls with `quantity <= remaining`. Receive loop: guard update on status
plus `quantity - received_qty >= :q`; TRANSFER_IN ledger rows already carry per-receipt
quantities. Status flips to RECEIVED only when fully received (conditional UPDATE
predicate extended with the same arithmetic). Dispatch already moved full quantity out,
so partial receipts need no source interaction. Ledger stays complete automatically.

## 3. Order cancellation releasing reservations

Current seam: reservations exist as `reserved_qty` deltas linked to orders via
ledger `reference_type=CUSTOMER_ORDER, reference_id=orderId`; order status enum already
contains `CANCELLED`.

Change: endpoint `POST /api/orders/{id}/cancel` (SALES/ADMIN). Service loads lines,
calls existing `InventoryLedger.release(...)` per line inside one transaction, then
status-guards `UPDATE customer_orders SET status='CANCELLED' WHERE id=:id AND
status='CREATED'` — same idempotency pattern as transfer receipt. Zero new inventory
concepts; release path is already concurrency-safe and tested.

## 4. Users restricted to assigned locations

Current seam: every stock-touching service call already receives an explicit
`locationId` argument; there is no hidden global location context.

Change: add `user_locations(user_id, location_id)` table + admin UI/API to assign.
Enforce in one place — a location-access check component invoked at the top of each
stock operation (or a Spring Security permission evaluator fed by the user's allowed
location ids). Because no code assumes "any location", enforcement is additive:
deny-list nothing, allow-list everything assigned. Work-order assignment validation
extends similarly (`assigned_user_id` must hold the location).

## General principle

Business rules live in services behind narrow interfaces (`InventoryLedger`,
state-transition guards), state machines are enforced by conditional updates, and all
invariants have DB-level backstops. New rules therefore arrive as *new predicates or new
ledger types*, not rewrites.
