-- =============================================================
-- FASAL Seed Data
-- Run this file AFTER schema.sql. Populates the database with
-- realistic sample data so the routing demo is interesting.
-- =============================================================

USE fasal_db;

-- -------------------------------------------------------------
-- HUBS
-- Six Indian cities chosen for agricultural / logistical weight.
-- -------------------------------------------------------------
-- Delhi: national capital, huge consumer market and a key dispatch point.
INSERT INTO hubs (id, name, city, latitude, longitude) VALUES (1, 'Delhi Hub',     'Delhi',     28.6139, 77.2090);
-- Mumbai: western financial centre with India's busiest port for produce export.
INSERT INTO hubs (id, name, city, latitude, longitude) VALUES (2, 'Mumbai Hub',    'Mumbai',    19.0760, 72.8777);
-- Nagpur: central India, the "Orange City"; major surplus producer of citrus and mango.
INSERT INTO hubs (id, name, city, latitude, longitude) VALUES (3, 'Nagpur Hub',    'Nagpur',    21.1458, 79.0882);
-- Chennai: southern port and a heavy rice-consuming region.
INSERT INTO hubs (id, name, city, latitude, longitude) VALUES (4, 'Chennai Hub',   'Chennai',   13.0827, 80.2707);
-- Kolkata: eastern port with a very dense urban population needing constant supply.
INSERT INTO hubs (id, name, city, latitude, longitude) VALUES (5, 'Kolkata Hub',   'Kolkata',   22.5726, 88.3639);
-- Ahmedabad: western state with strong dairy cooperatives and grain output.
INSERT INTO hubs (id, name, city, latitude, longitude) VALUES (6, 'Ahmedabad Hub', 'Ahmedabad', 23.0225, 72.5714);

-- -------------------------------------------------------------
-- HUB DISTANCES (full 6 x 6 matrix, both directions)
-- Approximate road distance in km and travel time in hours.
-- -------------------------------------------------------------
-- Delhi to Mumbai
INSERT INTO hub_distances VALUES (1, 2, 1400.0, 24.0);
-- Mumbai to Delhi
INSERT INTO hub_distances VALUES (2, 1, 1400.0, 24.0);
-- Delhi to Nagpur
INSERT INTO hub_distances VALUES (1, 3, 1050.0, 17.0);
-- Nagpur to Delhi
INSERT INTO hub_distances VALUES (3, 1, 1050.0, 17.0);
-- Delhi to Chennai
INSERT INTO hub_distances VALUES (1, 4, 2200.0, 35.0);
-- Chennai to Delhi
INSERT INTO hub_distances VALUES (4, 1, 2200.0, 35.0);
-- Delhi to Kolkata
INSERT INTO hub_distances VALUES (1, 5, 1500.0, 24.0);
-- Kolkata to Delhi
INSERT INTO hub_distances VALUES (5, 1, 1500.0, 24.0);
-- Delhi to Ahmedabad
INSERT INTO hub_distances VALUES (1, 6, 950.0, 15.0);
-- Ahmedabad to Delhi
INSERT INTO hub_distances VALUES (6, 1, 950.0, 15.0);
-- Mumbai to Nagpur
INSERT INTO hub_distances VALUES (2, 3, 830.0, 14.0);
-- Nagpur to Mumbai
INSERT INTO hub_distances VALUES (3, 2, 830.0, 14.0);
-- Mumbai to Chennai
INSERT INTO hub_distances VALUES (2, 4, 1340.0, 22.0);
-- Chennai to Mumbai
INSERT INTO hub_distances VALUES (4, 2, 1340.0, 22.0);
-- Mumbai to Kolkata
INSERT INTO hub_distances VALUES (2, 5, 2000.0, 32.0);
-- Kolkata to Mumbai
INSERT INTO hub_distances VALUES (5, 2, 2000.0, 32.0);
-- Mumbai to Ahmedabad
INSERT INTO hub_distances VALUES (2, 6, 530.0, 9.0);
-- Ahmedabad to Mumbai
INSERT INTO hub_distances VALUES (6, 2, 530.0, 9.0);
-- Nagpur to Chennai
INSERT INTO hub_distances VALUES (3, 4, 1170.0, 19.0);
-- Chennai to Nagpur
INSERT INTO hub_distances VALUES (4, 3, 1170.0, 19.0);
-- Nagpur to Kolkata
INSERT INTO hub_distances VALUES (3, 5, 1140.0, 19.0);
-- Kolkata to Nagpur
INSERT INTO hub_distances VALUES (5, 3, 1140.0, 19.0);
-- Nagpur to Ahmedabad
INSERT INTO hub_distances VALUES (3, 6, 1010.0, 16.0);
-- Ahmedabad to Nagpur
INSERT INTO hub_distances VALUES (6, 3, 1010.0, 16.0);
-- Chennai to Kolkata
INSERT INTO hub_distances VALUES (4, 5, 1670.0, 28.0);
-- Kolkata to Chennai
INSERT INTO hub_distances VALUES (5, 4, 1670.0, 28.0);
-- Chennai to Ahmedabad
INSERT INTO hub_distances VALUES (4, 6, 1820.0, 30.0);
-- Ahmedabad to Chennai
INSERT INTO hub_distances VALUES (6, 4, 1820.0, 30.0);
-- Kolkata to Ahmedabad
INSERT INTO hub_distances VALUES (5, 6, 2150.0, 35.0);
-- Ahmedabad to Kolkata
INSERT INTO hub_distances VALUES (6, 5, 2150.0, 35.0);

-- -------------------------------------------------------------
-- SPOKES (3 per hub = 18 total)
-- -------------------------------------------------------------
-- Delhi region spokes
INSERT INTO spokes (id, name, hub_id, latitude, longitude) VALUES (1, 'Sonipat',        1, 28.9931, 77.0151);
INSERT INTO spokes (id, name, hub_id, latitude, longitude) VALUES (2, 'Faridabad',      1, 28.4089, 77.3178);
INSERT INTO spokes (id, name, hub_id, latitude, longitude) VALUES (3, 'Ghaziabad',      1, 28.6692, 77.4538);
-- Mumbai region spokes
INSERT INTO spokes (id, name, hub_id, latitude, longitude) VALUES (4, 'Thane',          2, 19.2183, 72.9781);
INSERT INTO spokes (id, name, hub_id, latitude, longitude) VALUES (5, 'Panvel',         2, 18.9894, 73.1175);
INSERT INTO spokes (id, name, hub_id, latitude, longitude) VALUES (6, 'Bhiwandi',       2, 19.3002, 73.0635);
-- Nagpur region spokes
INSERT INTO spokes (id, name, hub_id, latitude, longitude) VALUES (7, 'Kamptee',        3, 21.2300, 79.1950);
INSERT INTO spokes (id, name, hub_id, latitude, longitude) VALUES (8, 'Hingna',         3, 21.0700, 78.9900);
INSERT INTO spokes (id, name, hub_id, latitude, longitude) VALUES (9, 'Saoner',         3, 21.3850, 78.9100);
-- Chennai region spokes
INSERT INTO spokes (id, name, hub_id, latitude, longitude) VALUES (10, 'Tambaram',      4, 12.9229, 80.1275);
INSERT INTO spokes (id, name, hub_id, latitude, longitude) VALUES (11, 'Avadi',         4, 13.1147, 80.1006);
INSERT INTO spokes (id, name, hub_id, latitude, longitude) VALUES (12, 'Sriperumbudur', 4, 12.9694, 79.9433);
-- Kolkata region spokes
INSERT INTO spokes (id, name, hub_id, latitude, longitude) VALUES (13, 'Howrah',        5, 22.5958, 88.2636);
INSERT INTO spokes (id, name, hub_id, latitude, longitude) VALUES (14, 'Barasat',       5, 22.7244, 88.4811);
INSERT INTO spokes (id, name, hub_id, latitude, longitude) VALUES (15, 'Salt Lake',     5, 22.5808, 88.4178);
-- Ahmedabad region spokes
INSERT INTO spokes (id, name, hub_id, latitude, longitude) VALUES (16, 'Sanand',        6, 22.9919, 72.3819);
INSERT INTO spokes (id, name, hub_id, latitude, longitude) VALUES (17, 'Bavla',         6, 22.8300, 72.3600);
INSERT INTO spokes (id, name, hub_id, latitude, longitude) VALUES (18, 'Mehmedabad',    6, 22.8281, 72.7561);

-- -------------------------------------------------------------
-- PRODUCE TYPES
-- lambda = decay rate per day in Q(t) = e^(-lambda * t).
-- Higher lambda => spoils faster.
-- -------------------------------------------------------------
-- milk: spoils within 1-2 days unrefrigerated, so very high decay rate.
INSERT INTO produce_types (id, name, lambda_value, unit) VALUES (1, 'milk',    0.45,  'liters');
-- spinach: tender leaves wilt within 2-3 days; very high decay rate.
INSERT INTO produce_types (id, name, lambda_value, unit) VALUES (2, 'spinach', 0.35,  'kg');
-- banana: usable for ~5-7 days; medium decay rate.
INSERT INTO produce_types (id, name, lambda_value, unit) VALUES (3, 'banana',  0.12,  'kg');
-- tomato: usable for ~7-10 days; medium decay rate.
INSERT INTO produce_types (id, name, lambda_value, unit) VALUES (4, 'tomato',  0.10,  'kg');
-- mango: usable for ~8-12 days when ripening; medium decay rate.
INSERT INTO produce_types (id, name, lambda_value, unit) VALUES (5, 'mango',   0.08,  'kg');
-- apple: lasts ~30-40 days at room temperature; low decay rate.
INSERT INTO produce_types (id, name, lambda_value, unit) VALUES (6, 'apple',   0.025, 'kg');
-- potato: keeps for many weeks if dry and cool; very low decay rate.
INSERT INTO produce_types (id, name, lambda_value, unit) VALUES (7, 'potato',  0.015, 'kg');
-- onion: similar to potato, very low decay rate.
INSERT INTO produce_types (id, name, lambda_value, unit) VALUES (8, 'onion',   0.018, 'kg');
-- wheat: stored grain lasts for many months; near-zero decay rate.
INSERT INTO produce_types (id, name, lambda_value, unit) VALUES (9, 'wheat',   0.002, 'kg');
-- rice: like wheat, stable for very long; near-zero decay rate.
INSERT INTO produce_types (id, name, lambda_value, unit) VALUES (10, 'rice',   0.001, 'kg');

-- -------------------------------------------------------------
-- USERS
-- Passwords stored as SHA-256 hex digests via MySQL SHA2().
-- -------------------------------------------------------------
INSERT INTO users (id, name, phone, password_hash, role, spoke_id, hub_id)
  VALUES (1, 'FASAL Admin', '9000000000', SHA2('admin123', 256), 'SUPER_ADMIN', NULL, NULL);
INSERT INTO users (id, name, phone, password_hash, role, spoke_id, hub_id)
  VALUES (2, 'Admin Delhi',     '9000000001', SHA2('hub123', 256), 'HUB_ADMIN', NULL, 1);
INSERT INTO users (id, name, phone, password_hash, role, spoke_id, hub_id)
  VALUES (3, 'Admin Mumbai',    '9000000002', SHA2('hub123', 256), 'HUB_ADMIN', NULL, 2);
INSERT INTO users (id, name, phone, password_hash, role, spoke_id, hub_id)
  VALUES (4, 'Admin Nagpur',    '9000000003', SHA2('hub123', 256), 'HUB_ADMIN', NULL, 3);
INSERT INTO users (id, name, phone, password_hash, role, spoke_id, hub_id)
  VALUES (5, 'Admin Chennai',   '9000000004', SHA2('hub123', 256), 'HUB_ADMIN', NULL, 4);
INSERT INTO users (id, name, phone, password_hash, role, spoke_id, hub_id)
  VALUES (6, 'Admin Kolkata',   '9000000005', SHA2('hub123', 256), 'HUB_ADMIN', NULL, 5);
INSERT INTO users (id, name, phone, password_hash, role, spoke_id, hub_id)
  VALUES (7, 'Admin Ahmedabad', '9000000006', SHA2('hub123', 256), 'HUB_ADMIN', NULL, 6);
INSERT INTO users (id, name, phone, password_hash, role, spoke_id, hub_id)
  VALUES (8, 'Ravi Kumar',   '9100000001', SHA2('farmer123', 256), 'FARMER', 1,  NULL);
INSERT INTO users (id, name, phone, password_hash, role, spoke_id, hub_id)
  VALUES (9, 'Sunita Devi',  '9100000002', SHA2('farmer123', 256), 'FARMER', 7,  NULL);
INSERT INTO users (id, name, phone, password_hash, role, spoke_id, hub_id)
  VALUES (10, 'Mohan Singh', '9100000003', SHA2('farmer123', 256), 'FARMER', 16, NULL);

-- -------------------------------------------------------------
-- INVENTORY
-- -------------------------------------------------------------
INSERT INTO inventory (hub_id, produce_type_id, quantity_kg, avg_harvest_date) VALUES (1, 4,  500, DATE_SUB(CURDATE(), INTERVAL 5  DAY));
INSERT INTO inventory (hub_id, produce_type_id, quantity_kg, avg_harvest_date) VALUES (1, 8,  400, DATE_SUB(CURDATE(), INTERVAL 8  DAY));
INSERT INTO inventory (hub_id, produce_type_id, quantity_kg, avg_harvest_date) VALUES (1, 9,  600, DATE_SUB(CURDATE(), INTERVAL 60 DAY));
INSERT INTO inventory (hub_id, produce_type_id, quantity_kg, avg_harvest_date) VALUES (1, 7,  300, DATE_SUB(CURDATE(), INTERVAL 30 DAY));
INSERT INTO inventory (hub_id, produce_type_id, quantity_kg, avg_harvest_date) VALUES (2, 3,  350, DATE_SUB(CURDATE(), INTERVAL 4  DAY));
INSERT INTO inventory (hub_id, produce_type_id, quantity_kg, avg_harvest_date) VALUES (2, 10, 800, DATE_SUB(CURDATE(), INTERVAL 90 DAY));
INSERT INTO inventory (hub_id, produce_type_id, quantity_kg, avg_harvest_date) VALUES (2, 8,  100, DATE_SUB(CURDATE(), INTERVAL 6  DAY));
INSERT INTO inventory (hub_id, produce_type_id, quantity_kg, avg_harvest_date) VALUES (2, 2,  80,  DATE_SUB(CURDATE(), INTERVAL 2  DAY));
INSERT INTO inventory (hub_id, produce_type_id, quantity_kg, avg_harvest_date) VALUES (3, 5,  600, DATE_SUB(CURDATE(), INTERVAL 6  DAY));
INSERT INTO inventory (hub_id, produce_type_id, quantity_kg, avg_harvest_date) VALUES (3, 9,  400, DATE_SUB(CURDATE(), INTERVAL 70 DAY));
INSERT INTO inventory (hub_id, produce_type_id, quantity_kg, avg_harvest_date) VALUES (3, 4,  100, DATE_SUB(CURDATE(), INTERVAL 5  DAY));
INSERT INTO inventory (hub_id, produce_type_id, quantity_kg, avg_harvest_date) VALUES (3, 6,  200, DATE_SUB(CURDATE(), INTERVAL 10 DAY));
INSERT INTO inventory (hub_id, produce_type_id, quantity_kg, avg_harvest_date) VALUES (4, 10, 500, DATE_SUB(CURDATE(), INTERVAL 80 DAY));
INSERT INTO inventory (hub_id, produce_type_id, quantity_kg, avg_harvest_date) VALUES (4, 2,  50,  DATE_SUB(CURDATE(), INTERVAL 2  DAY));
INSERT INTO inventory (hub_id, produce_type_id, quantity_kg, avg_harvest_date) VALUES (4, 3,  150, DATE_SUB(CURDATE(), INTERVAL 5  DAY));
INSERT INTO inventory (hub_id, produce_type_id, quantity_kg, avg_harvest_date) VALUES (5, 7,  700, DATE_SUB(CURDATE(), INTERVAL 25 DAY));
INSERT INTO inventory (hub_id, produce_type_id, quantity_kg, avg_harvest_date) VALUES (5, 10, 600, DATE_SUB(CURDATE(), INTERVAL 100 DAY));
INSERT INTO inventory (hub_id, produce_type_id, quantity_kg, avg_harvest_date) VALUES (5, 2,  80,  DATE_SUB(CURDATE(), INTERVAL 3  DAY));
INSERT INTO inventory (hub_id, produce_type_id, quantity_kg, avg_harvest_date) VALUES (5, 1,  50,  DATE_SUB(CURDATE(), INTERVAL 1  DAY));
INSERT INTO inventory (hub_id, produce_type_id, quantity_kg, avg_harvest_date) VALUES (6, 9,  800, DATE_SUB(CURDATE(), INTERVAL 50 DAY));
INSERT INTO inventory (hub_id, produce_type_id, quantity_kg, avg_harvest_date) VALUES (6, 1,  100, DATE_SUB(CURDATE(), INTERVAL 1  DAY));
INSERT INTO inventory (hub_id, produce_type_id, quantity_kg, avg_harvest_date) VALUES (6, 8,  250, DATE_SUB(CURDATE(), INTERVAL 7  DAY));

-- -------------------------------------------------------------
-- DEMAND
-- -------------------------------------------------------------
INSERT INTO demand (hub_id, produce_type_id, required_quantity_kg, min_quality_threshold) VALUES (1, 5,  300, 0.50);
INSERT INTO demand (hub_id, produce_type_id, required_quantity_kg, min_quality_threshold) VALUES (1, 2,  100, 0.60);
INSERT INTO demand (hub_id, produce_type_id, required_quantity_kg, min_quality_threshold) VALUES (2, 4,  300, 0.40);
INSERT INTO demand (hub_id, produce_type_id, required_quantity_kg, min_quality_threshold) VALUES (2, 9,  200, 0.30);
INSERT INTO demand (hub_id, produce_type_id, required_quantity_kg, min_quality_threshold) VALUES (3, 8,  150, 0.40);
INSERT INTO demand (hub_id, produce_type_id, required_quantity_kg, min_quality_threshold) VALUES (3, 10, 200, 0.20);
INSERT INTO demand (hub_id, produce_type_id, required_quantity_kg, min_quality_threshold) VALUES (4, 4,  400, 0.50);
INSERT INTO demand (hub_id, produce_type_id, required_quantity_kg, min_quality_threshold) VALUES (4, 8,  250, 0.50);
INSERT INTO demand (hub_id, produce_type_id, required_quantity_kg, min_quality_threshold) VALUES (4, 5,  350, 0.40);
INSERT INTO demand (hub_id, produce_type_id, required_quantity_kg, min_quality_threshold) VALUES (5, 5,  350, 0.40);
INSERT INTO demand (hub_id, produce_type_id, required_quantity_kg, min_quality_threshold) VALUES (5, 4,  200, 0.50);
INSERT INTO demand (hub_id, produce_type_id, required_quantity_kg, min_quality_threshold) VALUES (5, 1,  80,  0.70);
INSERT INTO demand (hub_id, produce_type_id, required_quantity_kg, min_quality_threshold) VALUES (6, 3,  200, 0.40);
INSERT INTO demand (hub_id, produce_type_id, required_quantity_kg, min_quality_threshold) VALUES (6, 2,  100, 0.60);

-- -------------------------------------------------------------
-- VEHICLES (2 per hub = 12 total, all IDLE, capacity 1000kg)
-- -------------------------------------------------------------
INSERT INTO vehicles (id, name, capacity_kg, current_hub_id, status) VALUES (1,  'Delhi-Truck-1',     1000, 1, 'IDLE');
INSERT INTO vehicles (id, name, capacity_kg, current_hub_id, status) VALUES (2,  'Delhi-Truck-2',     1000, 1, 'IDLE');
INSERT INTO vehicles (id, name, capacity_kg, current_hub_id, status) VALUES (3,  'Mumbai-Truck-1',    1000, 2, 'IDLE');
INSERT INTO vehicles (id, name, capacity_kg, current_hub_id, status) VALUES (4,  'Mumbai-Truck-2',    1000, 2, 'IDLE');
INSERT INTO vehicles (id, name, capacity_kg, current_hub_id, status) VALUES (5,  'Nagpur-Truck-1',    1000, 3, 'IDLE');
INSERT INTO vehicles (id, name, capacity_kg, current_hub_id, status) VALUES (6,  'Nagpur-Truck-2',    1000, 3, 'IDLE');
INSERT INTO vehicles (id, name, capacity_kg, current_hub_id, status) VALUES (7,  'Chennai-Truck-1',   1000, 4, 'IDLE');
INSERT INTO vehicles (id, name, capacity_kg, current_hub_id, status) VALUES (8,  'Chennai-Truck-2',   1000, 4, 'IDLE');
INSERT INTO vehicles (id, name, capacity_kg, current_hub_id, status) VALUES (9,  'Kolkata-Truck-1',   1000, 5, 'IDLE');
INSERT INTO vehicles (id, name, capacity_kg, current_hub_id, status) VALUES (10, 'Kolkata-Truck-2',   1000, 5, 'IDLE');
INSERT INTO vehicles (id, name, capacity_kg, current_hub_id, status) VALUES (11, 'Ahmedabad-Truck-1', 1000, 6, 'IDLE');
INSERT INTO vehicles (id, name, capacity_kg, current_hub_id, status) VALUES (12, 'Ahmedabad-Truck-2', 1000, 6, 'IDLE');
