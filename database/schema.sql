-- =============================================================
-- FASAL Database Schema
-- Run this file FIRST before seed.sql.
-- Creates the database and every table the backend needs.
-- =============================================================

CREATE DATABASE IF NOT EXISTS fasal_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE fasal_db;

-- Drop tables in reverse-dependency order so existing data is wiped cleanly.
DROP TABLE IF EXISTS route_cargo;
DROP TABLE IF EXISTS route_stops;
DROP TABLE IF EXISTS routes;
DROP TABLE IF EXISTS vehicles;
DROP TABLE IF EXISTS demand;
DROP TABLE IF EXISTS inventory;
DROP TABLE IF EXISTS produce_listings;
DROP TABLE IF EXISTS produce_types;
DROP TABLE IF EXISTS sessions;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS spokes;
DROP TABLE IF EXISTS hub_distances;
DROP TABLE IF EXISTS hubs;

-- Hubs are the main physical centres where produce is collected and dispatched.
CREATE TABLE hubs (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  city VARCHAR(100) NOT NULL,
  latitude DOUBLE NOT NULL,
  longitude DOUBLE NOT NULL
);

-- Driving distance and time between every ordered pair of hubs.
CREATE TABLE hub_distances (
  hub_id_from INT NOT NULL,
  hub_id_to INT NOT NULL,
  distance_km DOUBLE NOT NULL,
  travel_time_hours DOUBLE NOT NULL,
  PRIMARY KEY (hub_id_from, hub_id_to),
  FOREIGN KEY (hub_id_from) REFERENCES hubs(id),
  FOREIGN KEY (hub_id_to) REFERENCES hubs(id)
);

-- Spokes are smaller villages or towns each served by one parent hub.
CREATE TABLE spokes (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  hub_id INT NOT NULL,
  latitude DOUBLE NOT NULL,
  longitude DOUBLE NOT NULL,
  FOREIGN KEY (hub_id) REFERENCES hubs(id),
  INDEX idx_spokes_hub (hub_id)
);

-- All people who can log in: farmers, hub admins, and the single super admin.
CREATE TABLE users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  phone VARCHAR(15) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  role ENUM('FARMER','HUB_ADMIN','SUPER_ADMIN') NOT NULL,
  spoke_id INT NULL,
  hub_id INT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (spoke_id) REFERENCES spokes(id),
  FOREIGN KEY (hub_id) REFERENCES hubs(id),
  INDEX idx_users_phone (phone),
  INDEX idx_users_role (role)
);

-- Active login sessions: the token in the Authorization header maps to a user here.
CREATE TABLE sessions (
  id VARCHAR(64) PRIMARY KEY,
  user_id INT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id),
  INDEX idx_sessions_user (user_id)
);

-- Master list of produce types with their freshness decay rate (lambda).
CREATE TABLE produce_types (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  lambda_value DOUBLE NOT NULL,
  unit VARCHAR(20) DEFAULT 'kg'
);

-- Listings created by farmers when they harvest produce.
CREATE TABLE produce_listings (
  id INT AUTO_INCREMENT PRIMARY KEY,
  farmer_id INT NOT NULL,
  produce_type_id INT NOT NULL,
  quantity_kg DOUBLE NOT NULL,
  harvest_date DATE NOT NULL,
  hub_id INT NOT NULL,
  status ENUM('PENDING','IN_TRANSIT','DELIVERED') DEFAULT 'PENDING',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (farmer_id) REFERENCES users(id),
  FOREIGN KEY (produce_type_id) REFERENCES produce_types(id),
  FOREIGN KEY (hub_id) REFERENCES hubs(id),
  INDEX idx_listings_farmer (farmer_id),
  INDEX idx_listings_hub (hub_id)
);

-- Aggregated stock currently sitting at each hub.
CREATE TABLE inventory (
  id INT AUTO_INCREMENT PRIMARY KEY,
  hub_id INT NOT NULL,
  produce_type_id INT NOT NULL,
  quantity_kg DOUBLE NOT NULL,
  avg_harvest_date DATE NOT NULL,
  last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (hub_id) REFERENCES hubs(id),
  FOREIGN KEY (produce_type_id) REFERENCES produce_types(id),
  INDEX idx_inventory_hub (hub_id),
  INDEX idx_inventory_produce (produce_type_id)
);

-- How much of each produce a hub needs to receive, and the minimum freshness it requires.
CREATE TABLE demand (
  id INT AUTO_INCREMENT PRIMARY KEY,
  hub_id INT NOT NULL,
  produce_type_id INT NOT NULL,
  required_quantity_kg DOUBLE NOT NULL,
  min_quality_threshold DOUBLE NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (hub_id) REFERENCES hubs(id),
  FOREIGN KEY (produce_type_id) REFERENCES produce_types(id),
  INDEX idx_demand_hub (hub_id),
  INDEX idx_demand_produce (produce_type_id)
);

-- Trucks available for transporting produce between hubs.
CREATE TABLE vehicles (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  capacity_kg DOUBLE NOT NULL,
  current_hub_id INT NOT NULL,
  status ENUM('IDLE','IN_TRANSIT') DEFAULT 'IDLE',
  FOREIGN KEY (current_hub_id) REFERENCES hubs(id),
  INDEX idx_vehicles_hub (current_hub_id)
);

-- A planned, active, or completed delivery route assigned to one vehicle.
CREATE TABLE routes (
  id INT AUTO_INCREMENT PRIMARY KEY,
  vehicle_id INT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  status ENUM('PLANNED','ACTIVE','COMPLETED') DEFAULT 'PLANNED',
  requires_cold_storage BOOLEAN DEFAULT FALSE,
  FOREIGN KEY (vehicle_id) REFERENCES vehicles(id),
  INDEX idx_routes_vehicle (vehicle_id),
  INDEX idx_routes_status (status)
);

-- Ordered stops along a route - the path the truck will follow.
CREATE TABLE route_stops (
  id INT AUTO_INCREMENT PRIMARY KEY,
  route_id INT NOT NULL,
  hub_id INT NOT NULL,
  stop_order INT NOT NULL,
  arrived_at TIMESTAMP NULL,
  FOREIGN KEY (route_id) REFERENCES routes(id),
  FOREIGN KEY (hub_id) REFERENCES hubs(id),
  INDEX idx_route_stops_route (route_id)
);

-- The actual produce being carried on a route, with source/destination hubs.
CREATE TABLE route_cargo (
  id INT AUTO_INCREMENT PRIMARY KEY,
  route_id INT NOT NULL,
  produce_type_id INT NOT NULL,
  quantity_kg DOUBLE NOT NULL,
  source_hub_id INT NOT NULL,
  destination_hub_id INT NOT NULL,
  FOREIGN KEY (route_id) REFERENCES routes(id),
  FOREIGN KEY (produce_type_id) REFERENCES produce_types(id),
  FOREIGN KEY (source_hub_id) REFERENCES hubs(id),
  FOREIGN KEY (destination_hub_id) REFERENCES hubs(id),
  INDEX idx_route_cargo_route (route_id)
);
