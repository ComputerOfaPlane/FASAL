package com.fasal.services;

import com.fasal.algorithm.QualityCalculator;
import com.fasal.db.DatabaseConnection;
import com.fasal.models.Demand;
import com.fasal.models.Inventory;
import com.fasal.models.Route;
import com.fasal.models.RouteCargo;
import com.fasal.models.RouteStop;
import com.fasal.models.Vehicle;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// HubService groups the database operations a hub admin needs: inventory and
// demand at the hub, computed surplus, the vehicles parked at the hub, and
// the routes that involve the hub.
public class HubService {

    // Private constructor - this class is used statically.
    private HubService() { }

    // Returns the inventory at the given hub, with the live Q(t) added to each row.
    public static List<Inventory> getInventory(int hubId) throws SQLException {
        List<Inventory> list = new ArrayList<>();
        String sql = "SELECT i.id, i.hub_id, i.produce_type_id, i.quantity_kg, "
                   + "       i.avg_harvest_date, i.last_updated, "
                   + "       pt.name, pt.lambda_value "
                   + "  FROM inventory i "
                   + "  JOIN produce_types pt ON pt.id = i.produce_type_id "
                   + " WHERE i.hub_id = ? "
                   + " ORDER BY pt.name ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, hubId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Inventory inv = new Inventory();
                    inv.setId(rs.getInt("id"));
                    inv.setHubId(rs.getInt("hub_id"));
                    inv.setProduceTypeId(rs.getInt("produce_type_id"));
                    inv.setQuantityKg(rs.getDouble("quantity_kg"));
                    inv.setAvgHarvestDate(rs.getDate("avg_harvest_date").toLocalDate());
                    inv.setLastUpdated(rs.getTimestamp("last_updated"));
                    inv.setProduceName(rs.getString("name"));
                    inv.setLambdaValue(rs.getDouble("lambda_value"));
                    inv.setCurrentQuality(QualityCalculator.calculateQuality(
                        inv.getLambdaValue(), inv.getAvgHarvestDate()));
                    list.add(inv);
                }
            }
        }
        return list;
    }

    // Returns the demand rows at the given hub.
    public static List<Demand> getDemand(int hubId) throws SQLException {
        List<Demand> list = new ArrayList<>();
        String sql = "SELECT d.id, d.hub_id, d.produce_type_id, d.required_quantity_kg, "
                   + "       d.min_quality_threshold, d.created_at, "
                   + "       pt.name, pt.lambda_value "
                   + "  FROM demand d "
                   + "  JOIN produce_types pt ON pt.id = d.produce_type_id "
                   + " WHERE d.hub_id = ? "
                   + " ORDER BY pt.name ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, hubId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Demand d = new Demand();
                    d.setId(rs.getInt("id"));
                    d.setHubId(rs.getInt("hub_id"));
                    d.setProduceTypeId(rs.getInt("produce_type_id"));
                    d.setRequiredQuantityKg(rs.getDouble("required_quantity_kg"));
                    d.setMinQualityThreshold(rs.getDouble("min_quality_threshold"));
                    d.setCreatedAt(rs.getTimestamp("created_at"));
                    d.setProduceName(rs.getString("name"));
                    d.setLambdaValue(rs.getDouble("lambda_value"));
                    list.add(d);
                }
            }
        }
        return list;
    }

    // Returns one row per produce type at the hub showing inventory quantity,
    // local demand quantity, and the resulting surplus (positive or negative).
    public static List<Map<String, Object>> getSurplus(int hubId) throws SQLException {
        Map<Integer, Double> inventoryByType = new HashMap<>();
        Map<Integer, String> nameByType = new HashMap<>();
        String invSql = "SELECT i.produce_type_id, SUM(i.quantity_kg) AS qty, pt.name "
                      + "  FROM inventory i "
                      + "  JOIN produce_types pt ON pt.id = i.produce_type_id "
                      + " WHERE i.hub_id = ? "
                      + " GROUP BY i.produce_type_id, pt.name";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(invSql)) {
            ps.setInt(1, hubId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("produce_type_id");
                    inventoryByType.put(id, rs.getDouble("qty"));
                    nameByType.put(id, rs.getString("name"));
                }
            }
        }

        Map<Integer, Double> demandByType = new HashMap<>();
        String demSql = "SELECT d.produce_type_id, SUM(d.required_quantity_kg) AS qty, pt.name "
                      + "  FROM demand d "
                      + "  JOIN produce_types pt ON pt.id = d.produce_type_id "
                      + " WHERE d.hub_id = ? "
                      + " GROUP BY d.produce_type_id, pt.name";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(demSql)) {
            ps.setInt(1, hubId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("produce_type_id");
                    demandByType.put(id, rs.getDouble("qty"));
                    nameByType.putIfAbsent(id, rs.getString("name"));
                }
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        java.util.Set<Integer> allTypes = new java.util.TreeSet<>();
        allTypes.addAll(inventoryByType.keySet());
        allTypes.addAll(demandByType.keySet());
        for (int typeId : allTypes) {
            double inv = inventoryByType.getOrDefault(typeId, 0.0);
            double dem = demandByType.getOrDefault(typeId, 0.0);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("produce_type_id", typeId);
            row.put("produce_type",    nameByType.get(typeId));
            row.put("inventory_qty",   inv);
            row.put("demand_qty",      dem);
            row.put("surplus_qty",     inv - dem);
            result.add(row);
        }
        return result;
    }

    // Returns the vehicles currently parked at the given hub.
    public static List<Vehicle> getVehicles(int hubId) throws SQLException {
        List<Vehicle> list = new ArrayList<>();
        String sql = "SELECT id, name, capacity_kg, current_hub_id, status "
                   + "  FROM vehicles WHERE current_hub_id = ? ORDER BY id ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, hubId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Vehicle v = new Vehicle();
                    v.setId(rs.getInt("id"));
                    v.setName(rs.getString("name"));
                    v.setCapacityKg(rs.getDouble("capacity_kg"));
                    v.setCurrentHubId(rs.getInt("current_hub_id"));
                    v.setStatus(rs.getString("status"));
                    list.add(v);
                }
            }
        }
        return list;
    }

    // Returns recent routes that touch the given hub - either as a stop on the
    // route or as the source/destination of a piece of cargo.
    public static List<Route> getRoutes(int hubId) throws SQLException {
        String routeIdSql =
              "SELECT DISTINCT r.id "
            + "  FROM routes r "
            + "  LEFT JOIN route_stops s  ON s.route_id = r.id "
            + "  LEFT JOIN route_cargo c  ON c.route_id = r.id "
            + " WHERE s.hub_id = ? OR c.source_hub_id = ? OR c.destination_hub_id = ? "
            + " ORDER BY r.id DESC";

        List<Integer> routeIds = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(routeIdSql)) {
            ps.setInt(1, hubId);
            ps.setInt(2, hubId);
            ps.setInt(3, hubId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    routeIds.add(rs.getInt("id"));
                }
            }
        }
        return loadRoutesByIds(routeIds);
    }

    // Helper: given a list of route IDs, load the full Route objects with stops and cargo.
    public static List<Route> loadRoutesByIds(List<Integer> routeIds) throws SQLException {
        List<Route> result = new ArrayList<>();
        if (routeIds.isEmpty()) {
            return result;
        }
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < routeIds.size(); i++) {
            placeholders.append(i == 0 ? "?" : ",?");
        }
        String routeSql = "SELECT r.id, r.vehicle_id, r.created_at, r.status, "
                        + "       r.requires_cold_storage, v.name AS vehicle_name "
                        + "  FROM routes r "
                        + "  JOIN vehicles v ON v.id = r.vehicle_id "
                        + " WHERE r.id IN (" + placeholders + ") "
                        + " ORDER BY r.id DESC";

        Map<Integer, Route> byId = new LinkedHashMap<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(routeSql)) {
            for (int i = 0; i < routeIds.size(); i++) {
                ps.setInt(i + 1, routeIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Route r = new Route();
                    r.setId(rs.getInt("id"));
                    r.setVehicleId(rs.getInt("vehicle_id"));
                    r.setCreatedAt(rs.getTimestamp("created_at"));
                    r.setStatus(rs.getString("status"));
                    r.setRequiresColdStorage(rs.getBoolean("requires_cold_storage"));
                    r.setVehicleName(rs.getString("vehicle_name"));
                    byId.put(r.getId(), r);
                }
            }
        }

        String stopSql = "SELECT s.id, s.route_id, s.hub_id, s.stop_order, s.arrived_at, h.name "
                       + "  FROM route_stops s "
                       + "  JOIN hubs h ON h.id = s.hub_id "
                       + " WHERE s.route_id IN (" + placeholders + ") "
                       + " ORDER BY s.route_id, s.stop_order";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(stopSql)) {
            for (int i = 0; i < routeIds.size(); i++) {
                ps.setInt(i + 1, routeIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RouteStop s = new RouteStop();
                    s.setId(rs.getInt("id"));
                    s.setRouteId(rs.getInt("route_id"));
                    s.setHubId(rs.getInt("hub_id"));
                    s.setStopOrder(rs.getInt("stop_order"));
                    s.setArrivedAt(rs.getTimestamp("arrived_at"));
                    s.setHubName(rs.getString("name"));
                    Route r = byId.get(s.getRouteId());
                    if (r != null) {
                        r.getStops().add(s);
                    }
                }
            }
        }

        String cargoSql = "SELECT c.id, c.route_id, c.produce_type_id, c.quantity_kg, "
                        + "       c.source_hub_id, c.destination_hub_id, "
                        + "       pt.name AS produce_name, "
                        + "       hs.name AS source_name, hd.name AS dest_name "
                        + "  FROM route_cargo c "
                        + "  JOIN produce_types pt ON pt.id = c.produce_type_id "
                        + "  JOIN hubs hs ON hs.id = c.source_hub_id "
                        + "  JOIN hubs hd ON hd.id = c.destination_hub_id "
                        + " WHERE c.route_id IN (" + placeholders + ") "
                        + " ORDER BY c.route_id, c.id";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(cargoSql)) {
            for (int i = 0; i < routeIds.size(); i++) {
                ps.setInt(i + 1, routeIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RouteCargo c = new RouteCargo();
                    c.setId(rs.getInt("id"));
                    c.setRouteId(rs.getInt("route_id"));
                    c.setProduceTypeId(rs.getInt("produce_type_id"));
                    c.setQuantityKg(rs.getDouble("quantity_kg"));
                    c.setSourceHubId(rs.getInt("source_hub_id"));
                    c.setDestinationHubId(rs.getInt("destination_hub_id"));
                    c.setProduceName(rs.getString("produce_name"));
                    c.setSourceHubName(rs.getString("source_name"));
                    c.setDestinationHubName(rs.getString("dest_name"));
                    Route r = byId.get(c.getRouteId());
                    if (r != null) {
                        r.getCargo().add(c);
                    }
                }
            }
        }

        result.addAll(byId.values());
        return result;
    }
}
