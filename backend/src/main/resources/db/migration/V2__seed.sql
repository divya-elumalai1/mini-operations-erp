-- Development/test seed data. Credentials below are documented, non-production
-- development credentials (see README and .env.example). Never reuse in production.

-- Users: admin/Admin@123, ops/Ops@123, sales/Sales@123 (BCrypt hashed)
INSERT INTO users (username, password_hash, full_name, role) VALUES
('admin', '$2a$10$uIfWkXINhWO6NFhVmLWyBuhY.o2bdPrjdvZq1ycxkTdecHPW29lFO', 'Asha Admin',   'ADMIN'),
('ops',   '$2a$10$ywOuorYDgpZIsF39rOG9h.UfQDXyF0iahfoLbdY6ndAEdChmvgEiW', 'Om Prakash',   'OPERATIONS_USER'),
('sales', '$2a$10$syMf2YL4wcIzFGZ6WXQ0MuIPEZdEGb0t/eYxzDLPImJFXLBznqXli', 'Sara Sales',   'SALES_USER');

INSERT INTO locations (code, name) VALUES
('BLR-WH01',   'Bangalore Main Warehouse'),
('BLR-SHOP01', 'Bangalore Shop Floor'),
('MUM-WH01',   'Mumbai Warehouse');

INSERT INTO categories (name) VALUES
('RAW_MATERIAL'), ('FINISHED_GOODS'), ('SPARES');

INSERT INTO items (sku, name, category_id, unit_of_measure) VALUES
('RM-STEEL-ROD',  'Steel Rod 12mm',            1, 'METER'),
('FG-PUMP-A',     'Centrifugal Pump A100',     2, 'PCS'),
('FG-PUMP-B',     'Centrifugal Pump B200',     2, 'PCS'),
('SP-BEARING-25', 'Ball Bearing 25mm',         3, 'PCS');

-- Opening stock. Physical = on-hand; reserved starts at zero.
INSERT INTO inventory (item_id, location_id, physical_qty, reserved_qty) VALUES
(1, 1, 500.000, 0),  -- steel rods @ Bangalore WH
(1, 3, 300.000, 0),  -- steel rods @ Mumbai WH
(2, 1, 120.000, 0),
(3, 1, 70.000,  0),  -- deliberately low stock: handy for shortage demos
(4, 1, 40.000,  0),
(4, 2, 10.000,  0);

INSERT INTO batches (item_id, batch_code) VALUES
(1, 'LOT-2026-A'),
(2, 'FG-A-0726');

-- Example work order (assigned to ops user): pump A100 needs 25 bearings @ shop floor.
-- Availability is derived at request time; seeded stock makes this a shortage scenario.
INSERT INTO work_orders (wo_number, item_id, location_id, required_quantity, assigned_user_id, status, created_by)
SELECT 'WO-000001', i.id, l.id, 25.000, u.id, 'ASSIGNED', a.id
FROM items i, locations l, users u, users a
WHERE i.sku = 'SP-BEARING-25' AND l.code = 'BLR-SHOP01'
  AND u.username = 'ops' AND a.username = 'admin';

-- Example transfer awaiting dispatch: 50 m of steel rod Bangalore WH -> Shop Floor.
INSERT INTO internal_transfers (transfer_number, source_location_id, destination_location_id, item_id, quantity, status, created_by)
SELECT 'TRF-000001', s.id, d.id, i.id, 50.000, 'REQUESTED', u.id
FROM locations s, locations d, items i, users u
WHERE s.code = 'BLR-WH01' AND d.code = 'BLR-SHOP01'
  AND i.sku = 'RM-STEEL-ROD' AND u.username = 'ops';
