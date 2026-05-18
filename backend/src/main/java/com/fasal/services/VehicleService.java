package com.fasal.services;

import com.fasal.db.DatabaseConnection;
import com.fasal.models.Vehicle;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

// VehicleService handles reading and updating vehicle records.
public class VehicleService {

    // Private constructor - this class is used statically.
    private VehicleService() { }

    // Returns every vehicle in the fleet.
    public static List<Vehicle> getAll() throws SQLException {
        List<Vehicle> list = new ArrayList<>();
        String sql = "SELECT id, name, capacity_kg, current_hub_id, status "
                   + "  FROM vehicles ORDER BY id ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    // Returns every vehicle currently parked at the given hub.
    public static List<Vehicle> getByHub(int hubId) throws SQLException {
        List<Vehicle> list = new ArrayList<>();
        String sql = "SELECT id, name, capacity_kg, current_hub_id, status "
                   + "  FROM vehicles WHERE current_hub_id = ? ORDER BY id ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, hubId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    // Updates a vehicle's status (IDLE or IN_TRANSIT).
    public static void updateStatus(int vehicleId, String status) throws SQLException {
        String sql = "UPDATE vehicles SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, vehicleId);
            ps.executeUpdate();
        }
    }

    // Moves a vehicle to a different hub (used when a route completes).
    public static void updateCurrentHub(int vehicleId, int hubId) throws SQLException {
        String sql = "UPDATE vehicles SET current_hub_id = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, hubId);
            ps.setInt(2, vehicleId);
            ps.executeUpdate();
        }
    }

    // Helper: turns one result-set row into a Vehicle object.
    private static Vehicle mapRow(ResultSet rs) throws SQLException {
        Vehicle v = new Vehicle();
        v.setId(rs.getInt("id"));
        v.setName(rs.getString("name"));
        v.setCapacityKg(rs.getDouble("capacity_kg"));
        v.setCurrentHubId(rs.getInt("current_hub_id"));
        v.setStatus(rs.getString("status"));
        return v;
    }
}
