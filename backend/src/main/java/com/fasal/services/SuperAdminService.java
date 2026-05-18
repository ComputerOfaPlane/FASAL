package com.fasal.services;

import com.fasal.db.DatabaseConnection;
import com.fasal.models.Hub;
import com.fasal.models.Route;
import com.fasal.models.Spoke;
import com.fasal.models.Vehicle;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// SuperAdminService gives the super admin a system-wide view: every hub, every
// spoke, every vehicle, every route (with stops and cargo) and a small overview
// of summary counts.
public class SuperAdminService {

    // Status string used to count IDLE vehicles in the overview.
    private static final String VEHICLE_STATUS_IDLE = "IDLE";
    // Status strings used to count active routes in the overview.
    private static final String ROUTE_STATUS_PLANNED = "PLANNED";
    private static final String ROUTE_STATUS_ACTIVE  = "ACTIVE";

    // Private constructor - this class is used statically.
    private SuperAdminService() { }

    // Returns every hub in the system.
    public static List<Hub> getAllHubs() throws SQLException {
        List<Hub> list = new ArrayList<>();
        String sql = "SELECT id, name, city, latitude, longitude FROM hubs ORDER BY id ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Hub h = new Hub();
                h.setId(rs.getInt("id"));
                h.setName(rs.getString("name"));
                h.setCity(rs.getString("city"));
                h.setLatitude(rs.getDouble("latitude"));
                h.setLongitude(rs.getDouble("longitude"));
                list.add(h);
            }
        }
        return list;
    }

    // Returns every spoke in the system with its parent hub ID.
    public static List<Spoke> getAllSpokes() throws SQLException {
        List<Spoke> list = new ArrayList<>();
        String sql = "SELECT id, name, hub_id, latitude, longitude FROM spokes ORDER BY hub_id, id";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Spoke s = new Spoke();
                s.setId(rs.getInt("id"));
                s.setName(rs.getString("name"));
                s.setHubId(rs.getInt("hub_id"));
                s.setLatitude(rs.getDouble("latitude"));
                s.setLongitude(rs.getDouble("longitude"));
                list.add(s);
            }
        }
        return list;
    }

    // Returns every vehicle in the fleet.
    public static List<Vehicle> getAllVehicles() throws SQLException {
        return VehicleService.getAll();
    }

    // Returns every route with its stops and cargo, newest first.
    public static List<Route> getAllRoutes() throws SQLException {
        List<Integer> ids = new ArrayList<>();
        String sql = "SELECT id FROM routes ORDER BY id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getInt("id"));
            }
        }
        return HubService.loadRoutesByIds(ids);
    }

    // Returns a small summary used by the admin dashboard.
    public static Map<String, Object> getOverviewStats() throws SQLException {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_listings", countSimple("SELECT COUNT(*) FROM produce_listings"));
        stats.put("active_routes",  countWithTwoStatuses(
            "SELECT COUNT(*) FROM routes WHERE status = ? OR status = ?",
            ROUTE_STATUS_PLANNED, ROUTE_STATUS_ACTIVE));
        stats.put("idle_vehicles",  countWithOneStatus(
            "SELECT COUNT(*) FROM vehicles WHERE status = ?",
            VEHICLE_STATUS_IDLE));
        stats.put("hubs_count",     countSimple("SELECT COUNT(*) FROM hubs"));
        stats.put("total_users",    countSimple("SELECT COUNT(*) FROM users"));
        stats.put("total_vehicles", countSimple("SELECT COUNT(*) FROM vehicles"));
        return stats;
    }

    // Helper: runs a COUNT(*) query that takes no parameters.
    private static int countSimple(String sql) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // Helper: runs a COUNT(*) query with one status parameter.
    private static int countWithOneStatus(String sql, String status) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // Helper: runs a COUNT(*) query with two status parameters.
    private static int countWithTwoStatuses(String sql, String a, String b) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, a);
            ps.setString(2, b);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
