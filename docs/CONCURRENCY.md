# Concurrency Strategy

## Problem

Inventory correctness must survive simultaneous requests. Canonical failure mode:

> Available = 10. User A reserves 7 while User B reserves 7. Both must not succeed.

A naive `read → check available >= q → write` sequence has a TOCTOU race: both requests
read 10, both pass the check, both write, and the system oversells by 4.

## Options evaluated

| Mechanism | Verdict |
|---|---|
| Frontend-only checks | Rejected — never a security/correctness mechanism. |
| Plain check-then-write in a transaction | Rejected — READ COMMITTED does not prevent this race; both reads still see old values. |
| Pessimistic `SELECT ... FOR UPDATE` | Works, but requires every code path to remember the lock and a consistent lock ordering to avoid deadlocks; easy to misuse as the codebase grows. |
| Optimistic `@Version` retry | Works, but shifts contention into user-visible retries under load; also fails late (after work was done). |
| **Atomic conditional UPDATE** | **Chosen.** Single-statement, race-free by SQL semantics, no read-modify-write window. |

## Chosen mechanism: atomic conditional updates

All quantity mutations go through guarded UPDATE statements executed inside a
transaction (PostgreSQL, READ COMMITTED):

```sql
-- reserve q units of item :itemId at location :locationId
UPDATE inventory
   SET reserved_qty = reserved_qty + :q,
       updated_at   = now()
 WHERE item_id = :itemId
   AND location_id = :locationId
   AND physical_qty - reserved_qty >= :q
```

Why this cannot oversell:

1. When two transactions UPDATE the same row, PostgreSQL serializes them via the row
   lock — the second blocks until the first commits or rolls back.
2. After unblocking, the second re-evaluates the WHERE predicate against the **new**
   committed row version (READ COMMITTED re-check for updated rows). If A reserved 7 of
   10, B's predicate sees `available = 3 < 7` and matches zero rows.
3. Zero rows affected ⇒ service throws `InsufficientStockException` ⇒ transaction
   rolls back ⇒ no partial state.

The invariant is enforced *by the statement*, not by application discipline, so any
future caller of the ledger component inherits the safety.

### Complementary mechanisms

- **Upsert for stock-in / receive:** destination rows may not exist yet.
  `INSERT ... ON CONFLICT (item_id, location_id) DO UPDATE SET physical_qty = inventory.physical_qty + EXCLUDED.physical_qty`
  keeps creation + increment atomic without an extra round-trip race.
- **Optimistic locking (`@Version`) as a second net:** entity-path mutations would fail
  fast on stale versions; belt-and-braces alongside the predicates.
- **DB CHECK constraints** (`physical >= 0`, `0 <= reserved <= physical`) make even a
  hypothetical buggy writer abort the transaction at commit time.
- **Deterministic lock ordering:** multi-line operations (customer orders) sort line
  items by `(item_id, location_id)` before mutating, preventing deadlocks between
  concurrent multi-line orders.
- **State-guarded transitions:** transfer receipt is
  `UPDATE internal_transfers SET status='RECEIVED' ... WHERE id=:id AND status='DISPATCHED'`;
  two concurrent receives → exactly one update wins, the loser sees 0 rows and fails.

## Proof

`ConcurrentReservationIT` spins up real threads against the real database:
N concurrent reservations for the last units of stock produce exactly one success,
and final `reserved_qty == initial available`. Transfer tests prove dispatch/receive
races similarly (`TransferIT.duplicateReceiptRejected`).
