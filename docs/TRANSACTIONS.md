# Transaction Boundaries

Principle: **one business operation = one database transaction**, owned by the service
method. Controllers never open transactions; repositories never decide scope.

## Boundary map

| Operation | Method | Writes (all-or-nothing) |
|---|---|---|
| Goods receipt | `InventoryService.receipt(...)` | inventory upsert (+ledger row) |
| Stock reservation | `InventoryLedger.reserve(...)` | reserved_qty update (+RESERVE ledger row) |
| Reservation release | `InventoryLedger.release(...)` | reserved_qty update (+RELEASE ledger row) |
| Transfer create | `TransferService.create(...)` | transfer row (REQUESTED) — no stock movement yet |
| **Transfer dispatch** | `TransferService.dispatch(...)` | source physical ↓ (conditional), status→DISPATCHED, TRANSFER_OUT ledger |
| **Transfer receive** | `TransferService.receive(...)` | dest physical ↑ (upsert), status-guarded →RECEIVED, TRANSFER_IN ledger |
| **Customer order** | `OrderService.createOrderWithReservation(...)` | order + line items + N reservations + N RESERVE ledger rows |
| Work order ops | create/changeStatus | work-order row only (no stock movement by design) |

Bold rows mutate multiple related records; if any step throws, the whole unit rolls back
— e.g., a customer order whose third line hits insufficient stock leaves **no** order
row, **no** reservation on lines 1–2, and **no** ledger entries. Verified by
`OrderRollbackIT`.

## Rules applied

- `@Transactional` sits on service methods only (read-only variants for queries).
- Domain exceptions propagate out of the transactional proxy ⇒ rollback by default.
  No exception swallowing inside service methods.
- Ledger inserts share the boundary transaction so audit state can never diverge from
  stock state.
- Business-number sequences (`wo_number_seq`, …) use `nextval` — sequence values are
  non-transactional but that is acceptable: gaps in numbering carry no meaning here.
- No nested/requires-new transactions anywhere; simplicity is deliberate.
- Read endpoints run read-only transactions, giving Hibernate consistency guarantees
  without write locks.
