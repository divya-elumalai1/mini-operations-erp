# Interview Prep — Answers Grounded in This Codebase

**Why this database structure?**
Normalized masters (users/locations/categories/items) plus operational tables: one
`inventory` row per `(item, location)` holding mutable stock state, an append-only
`inventory_transactions` ledger, and lifecycle tables (`work_orders`,
`internal_transfers`, `customer_orders[_items]`). Quantities are NUMERIC(14,3); CHECK
constraints back every invariant. See `V1__schema.sql`.

**How is available inventory calculated?**
Derived, never stored: `available = physical − reserved`. Java side:
`Inventory.getAvailableQty()`; SQL side: the predicate inside every guarded update.

**How do you prevent negative inventory?**
Three layers: (1) all mutations go through guarded conditional updates requiring
sufficient quantity; (2) DB CHECK constraints abort violations even if a bug bypassed
services; (3) `InventoryLedger` is the only mutation path (`Propagation.MANDATORY`
forces callers to own a transaction).

**How do you prevent reservation races?**
Atomic conditional UPDATE:
`UPDATE inventory SET reserved_qty = reserved_qty + q
WHERE item_id=? AND location_id=? AND physical_qty - reserved_qty >= q`.
Under READ COMMITTED, competing UPDATEs serialize on the row lock; the loser
re-evaluates the predicate against committed data, matches zero rows ⇒ exception ⇒
rollback. Full analysis: `docs/CONCURRENCY.md`.

**What happens if two users reserve the last units simultaneously?**
Exactly one succeeds. Proven by `concurrentReservationsNeverOversell`: 8 threads race
for 70 units in 10-unit orders — 7 succeed, 1 gets 409 INSUFFICIENT_STOCK, final
reserved == 70.

**What happens if transfer receipt is called twice?**
Sequential duplicate: service sees status RECEIVED and throws before touching stock.
Concurrent duplicate: both pass the check, but `markReceived` is
`UPDATE … WHERE status='DISPATCHED'` — one wins; the loser updates zero rows and
throws, rolling back its destination credit. Covered by tests.

**Why doesn't destination inventory increase at dispatch?**
Goods are in transit; crediting early would create phantom sellable stock. Dispatch
debits source atomically with the status flip; receipt credits destination in its own
unit. Separate ledger types TRANSFER_OUT / TRANSFER_IN.

**Where are transaction boundaries?**
On service methods — one per business operation: receipt, reserve, dispatch, receive,
order-with-reservation. Ledger inserts share the caller's boundary so audit can never
diverge from stock. Map: `docs/TRANSACTIONS.md`.

**How does backend authorization work?**
JWT carries a signed role claim; the filter rebuilds authorities from it; endpoints
declare `@PreAuthorize`; security entry points return JSON envelopes (401/403).
Frontend hiding is UX only. Verified across 12 role cases.

**How are passwords stored?**
BCrypt via Spring Security's encoder. Seed hashes generated with the same encoder;
plaintext never persists. Login failures are deliberately vague (no user enumeration).

**How would you implement partial transfer receipt?**
Add `received_qty`; accept `quantity ≤ remaining`; extend the status-guard predicate to
flip RECEIVED only when fully received. Ledger already records per-receipt quantities.
See `docs/FUTURE-CHANGES.md`.

**How would you release stock on order cancellation?**
Reservations link to orders via ledger references. Cancel = one transaction calling the
existing `InventoryLedger.release()` per line + status-guarded UPDATE. Release is
already concurrency-safe and tested.

**How would you restrict users to assigned locations?**
Every operation already takes explicit location ids — no hidden global context. Add
`user_locations(user_id, location_id)` plus one allow-list check at the top of
stock-touching services. See FUTURE-CHANGES.

**How would you add damaged stock?**
New `damaged_qty` column + availability formula becomes `physical − reserved − damaged`
in the single calculator + new ledger types + same guarded-update pattern.

**How would you debug a failed transaction?**
Reproduce with the integration-test harness (real Postgres, deterministic reset), enable
SQL logging, find the statement that returned zero rows (guard failure) or the aborted
constraint, correlate with the ledger — every successful movement is recorded with actor,
deltas and reference, so post-mortems are factual.
