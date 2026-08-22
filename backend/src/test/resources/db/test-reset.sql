-- Deterministic per-test database reset: truncates all business tables and reseeds
-- the standard fixture set (same shape as V2__seed.sql) so every test starts from a
-- known state with stable ids.

TRUNCATE inventory_transactions, customer_order_items, customer_orders,
         internal_transfers, work_orders, inventory, batches, items,
         categories, locations, users
RESTART IDENTITY CASCADE;

ALTER SEQUENCE wo_number_seq  RESTART WITH 2;
ALTER SEQUENCE trf_number_seq RESTART WITH 2;
ALTER SEQUENCE so_number_seq  RESTART WITH 1;

INSERT INTO users (username, password_hash, full_name, role) VALUES
('admin', '$2a$10$uIfWkXINhWO6NFhVmLWyBuhY.o2bdPrjdvZq1ycxkTdecHPW29lFO', 'Asha Admin', 'ADMIN'),
('ops',   '$2a$10$ywOuorYDgpZIsF39rOG9h.UfQDXyF0iahfoLbdY6ndAEdChmvgEiW', 'Om Prakash', 'OPERATIONS_USER'),
('sales', '$2a$10$syMf2YL4wcIzFGZ6WXQ0MuIPEZdEGb0t/eYxzDLPImJFXLBznqXli', 'Sara Sales', 'SALES_USER');

INSERT INTO locations (code, name) VALUES
('BLR-WH01',   'Bangalore Main Warehouse'),
('BLR-SHOP01', 'Bangalore Shop Floor'),
('MUM-WH01',   'Mumbai Warehouse');

INSERT INTO categories (name) VALUES ('RAW_MATERIAL'), ('FINISHED_GOODS'), ('SPARES');

INSERT INTO items (sku, name, category_id, unit_of_measure) VALUES
('RM-STEEL-ROD',  'Steel Rod 12mm',          1, 'METER'),
('FG-PUMP-A',     'Centrifugal Pump A100',   2, 'PCS'),
('FG-PUMP-B',     'Centrifugal Pump B200',   2, 'PCS'),
('SP-BEARING-25', 'Ball Bearing 25mm',       3, 'PCS');

INSERT INTO batches (item_id, batch_code) VALUES (1, 'LOT-2026-A'), (2, 'FG-A-0726');

-- Stable opening stock:
--   item 1 rods:   500 @ loc 1, 300 @ loc 3
--   item 2 pumpA:  120 @ loc 1
--   item 3 pumpB:   70 @ loc 1   <- shortage/race scenarios
--   item 4 bearing: 40 @ loc 1, 10 @ loc 2
INSERT INTO inventory (item_id, location_id, physical_qty, reserved_qty) VALUES
(1, 1, 500.000, 0),
(1, 3, 300.000, 0),
(2, 1, 120.000, 0),
(3, 1, 70.000,  0),
(4, 1, 40.000,  0),
(4, 2, 10.000,  0);

INSERT INTO work_orders (wo_number, item_id, location_id, required_quantity, assigned_user_id, status, created_by)
VALUES ('WO-000001', 4, 2, 25.000, 2, 'ASSIGNED', 1);

INSERT INTO internal_transfers (transfer_number, source_location_id, destination_location_id, item_id, quantity, status, created_by)
VALUES ('TRF-000001', 1, 2, 1, 50.000, 'REQUESTED', 2);
