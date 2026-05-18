package com.fasal.services;

import com.fasal.algorithm.QualityCalculator;
import com.fasal.db.DatabaseConnection;
import com.fasal.models.Produce;
import com.fasal.models.ProduceType;
import com.fasal.models.Spoke;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// FarmerService groups the database operations a farmer user needs:
// creating a produce listing, listing their own past listings, listing every
// listing at a hub, and providing a master list of produce types and spokes
// for use in registration / form dropdowns.
public class FarmerService {

    // Status code used when a listing is first created.
    private static final String LISTING_STATUS_PENDING = "PENDING";

    // Private constructor - this class is used statically.
    private FarmerService() { }

    // Creates a new produce listing for the given farmer. The hub the listing
    // belongs to is derived from the farmer's spoke. Returns the new listing's ID.
    public static int createListing(int farmerId, int produceTypeId, double quantityKg,
                                     LocalDate harvestDate) throws SQLException {
        int hubId = findHubIdForFarmer(farmerId);

        String sql = "INSERT INTO produce_listings "
                   + "(farmer_id, produce_type_id, quantity_kg, harvest_date, hub_id, status) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, farmerId);
            ps.setInt(2, produceTypeId);
            ps.setDouble(3, quantityKg);
            ps.setDate(4, Date.valueOf(harvestDate));
            ps.setInt(5, hubId);
            ps.setString(6, LISTING_STATUS_PENDING);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        return 0;
    }

    // Returns every listing the given farmer has created, sorted newest first.
    // Quality Q(t) is calculated here in Java so it always reflects "right now".
    public static List<Produce> getListingsByFarmer(int farmerId) throws SQLException {
        String sql = "SELECT pl.id, pl.farmer_id, pl.produce_type_id, pl.quantity_kg, "
                   + "       pl.harvest_date, pl.hub_id, pl.status, pl.created_at, "
                   + "       pt.name, pt.lambda_value "
                   + "  FROM produce_listings pl "
                   + "  JOIN produce_types pt ON pt.id = pl.produce_type_id "
                   + " WHERE pl.farmer_id = ? "
                   + " ORDER BY pl.created_at DESC";
        return readListings(sql, farmerId);
    }

    // Returns every listing currently associated with the given hub.
    public static List<Produce> getAllListingsAtHub(int hubId) throws SQLException {
        String sql = "SELECT pl.id, pl.farmer_id, pl.produce_type_id, pl.quantity_kg, "
                   + "       pl.harvest_date, pl.hub_id, pl.status, pl.created_at, "
                   + "       pt.name, pt.lambda_value "
                   + "  FROM produce_listings pl "
                   + "  JOIN produce_types pt ON pt.id = pl.produce_type_id "
                   + " WHERE pl.hub_id = ? "
                   + " ORDER BY pl.created_at DESC";
        return readListings(sql, hubId);
    }

    // Returns the master list of produce types for use in dropdowns.
    public static List<ProduceType> getAllProduceTypes() throws SQLException {
        String sql = "SELECT id, name, lambda_value, unit FROM produce_types ORDER BY id ASC";
        List<ProduceType> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ProduceType pt = new ProduceType();
                pt.setId(rs.getInt("id"));
                pt.setName(rs.getString("name"));
                pt.setLambdaValue(rs.getDouble("lambda_value"));
                pt.setUnit(rs.getString("unit"));
                list.add(pt);
            }
        }
        return list;
    }

    // Returns every spoke in the system - used to populate the registration form's dropdown.
    public static List<Spoke> getAllSpokes() throws SQLException {
        String sql = "SELECT id, name, hub_id, latitude, longitude FROM spokes ORDER BY hub_id, id";
        List<Spoke> list = new ArrayList<>();
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

    // Helper: looks up the hub a farmer belongs to via their spoke.
    private static int findHubIdForFarmer(int farmerId) throws SQLException {
        String sql = "SELECT s.hub_id FROM users u JOIN spokes s ON s.id = u.spoke_id WHERE u.id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, farmerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("hub_id");
                }
            }
        }
        throw new SQLException("No spoke/hub mapping found for farmer id " + farmerId);
    }

    // Helper: shared logic for listing queries. Builds Produce objects and fills
    // in the live Q(t) freshness value.
    private static List<Produce> readListings(String sql, int filterValue) throws SQLException {
        List<Produce> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, filterValue);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Produce p = new Produce();
                    p.setId(rs.getInt("id"));
                    p.setFarmerId(rs.getInt("farmer_id"));
                    p.setProduceTypeId(rs.getInt("produce_type_id"));
                    p.setQuantityKg(rs.getDouble("quantity_kg"));
                    p.setHarvestDate(rs.getDate("harvest_date").toLocalDate());
                    p.setHubId(rs.getInt("hub_id"));
                    p.setStatus(rs.getString("status"));
                    p.setCreatedAt(rs.getTimestamp("created_at"));
                    p.setProduceName(rs.getString("name"));
                    p.setLambdaValue(rs.getDouble("lambda_value"));
                    p.setCurrentQuality(QualityCalculator.calculateQuality(
                        p.getLambdaValue(), p.getHarvestDate()));
                    list.add(p);
                }
            }
        }
        return list;
    }
}
