# Entity–Relationship Diagram

Matches `backend/src/main/resources/db/migration/V1__schema.sql` exactly.
Mermaid source: [`er-diagram.mmd`](er-diagram.mmd).

```mermaid
erDiagram
    USERS ||--o{ WORK_ORDERS : "assigned to"
    USERS ||--o{ WORK_ORDERS : created
    USERS ||--o{ INTERNAL_TRANSFERS : dispatched
    USERS ||--o{ INTERNAL_TRANSFERS : received
    USERS ||--o{ CUSTOMER_ORDERS : places
    USERS ||--o{ INVENTORY_TRANSACTIONS : performed

    CATEGORIES ||--o{ ITEMS : groups
    ITEMS ||--o{ BATCHES : "has lots"
    ITEMS ||--o{ INVENTORY : "stocked as"
    LOCATIONS ||--o{ INVENTORY : "holds"
    ITEMS ||--o{ INVENTORY_TRANSACTIONS : "moved"
    LOCATIONS ||--o{ INVENTORY_TRANSACTIONS : "at"
    BATCHES |o--o{ INVENTORY_TRANSACTIONS : "lot detail"

    ITEMS ||--o{ WORK_ORDERS : "required for"
    LOCATIONS ||--o{ WORK_ORDERS : "checked at"

    LOCATIONS ||--o{ INTERNAL_TRANSFERS : source
    LOCATIONS ||--o{ INTERNAL_TRANSFERS : destination
    ITEMS ||--o{ INTERNAL_TRANSFERS : transferred

    CUSTOMER_ORDERS ||--|{ CUSTOMER_ORDER_ITEMS : contains
    ITEMS ||--o{ CUSTOMER_ORDER_ITEMS : ordered
    LOCATIONS ||--o{ CUSTOMER_ORDER_ITEMS : "reserved at"

    USERS {
        bigint id PK
        varchar username UK
        varchar password_hash "BCrypt"
        varchar full_name
        varchar role "ADMIN | OPERATIONS_USER | SALES_USER"
        boolean active
    }

    LOCATIONS {
        bigint id PK
        varchar code UK
        varchar name
        boolean active
    }

    CATEGORIES {
        bigint id PK
        varchar name UK
    }

    ITEMS {
        bigint id PK
        varchar sku UK
        varchar name
        bigint category_id FK
        varchar unit_of_measure
        boolean active
    }

    BATCHES {
        bigint id PK
        bigint item_id FK
        varchar batch_code
        date expires_on
    }

    INVENTORY {
        bigint id PK
        bigint item_id FK "UK(item_id, location_id)"
        bigint location_id FK
        numeric physical_qty ">= 0"
        numeric reserved_qty ">= 0, <= physical_qty"
        bigint version "optimistic lock"
    }

    INVENTORY_TRANSACTIONS {
        bigint id PK
        bigint item_id FK
        bigint location_id FK
        bigint batch_id FK "nullable"
        varchar tx_type "GOODS_RECEIPT | RESERVE | RELEASE | TRANSFER_OUT | TRANSFER_IN | ADJUSTMENT | ISSUE"
        varchar reference_type "MANUAL | INTERNAL_TRANSFER | CUSTOMER_ORDER | WORK_ORDER"
        bigint reference_id
        numeric quantity_delta_physical
        numeric quantity_delta_reserved
        varchar idempotency_key UK "nullable"
        bigint performed_by FK
    }

    WORK_ORDERS {
        bigint id PK
        varchar wo_number UK
        bigint item_id FK
        bigint location_id FK
        numeric required_quantity "> 0"
        bigint assigned_user_id FK
        varchar status "ASSIGNED | IN_PROGRESS | COMPLETED"
        bigint created_by FK
    }

    INTERNAL_TRANSFERS {
        bigint id PK
        varchar transfer_number UK
        bigint source_location_id FK
        bigint destination_location_id FK "must differ from source"
        bigint item_id FK
        numeric quantity "> 0"
        varchar status "REQUESTED | DISPATCHED | RECEIVED"
        bigint dispatched_by FK
        timestamptz dispatched_at
        bigint received_by FK
        timestamptz received_at
        bigint created_by FK
    }

    CUSTOMER_ORDERS {
        bigint id PK
        varchar order_number UK
        varchar customer_name
        varchar status "CREATED | CANCELLED"
        bigint created_by FK
    }

    CUSTOMER_ORDER_ITEMS {
        bigint id PK
        bigint order_id FK "CASCADE"
        bigint item_id FK
        bigint location_id FK
        numeric quantity "> 0"
    }
```

## Cardinality notes

- One inventory row per `(item, location)` pair — enforced unique; quantity fields carry the stock state.
- `inventory_transactions` is append-only; every mutation of `inventory` writes a ledger row in the same transaction.
- A work order references exactly one item/location and its required quantity; shortage is derived, never stored.
- Transfer `source_location_id <> destination_location_id` is a DB CHECK constraint.
