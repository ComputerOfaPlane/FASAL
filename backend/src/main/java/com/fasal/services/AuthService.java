package com.fasal.services;

import com.fasal.db.DatabaseConnection;
import com.fasal.models.Farmer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// AuthService handles signup, login, and session token checks.
// Passwords are hashed with SHA-256 before being saved to the database.
public class AuthService {

    // Name of the hashing algorithm used for passwords.
    private static final String HASH_ALGORITHM = "SHA-256";
    // Format string that turns one byte into two lowercase hex characters.
    private static final String HEX_BYTE_FORMAT = "%02x";
    // Role string for a farmer account.
    public static final String ROLE_FARMER = "FARMER";
    // Role string for a hub admin account.
    public static final String ROLE_HUB_ADMIN = "HUB_ADMIN";
    // Role string for the super admin account.
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    // Private constructor - this class is used statically.
    private AuthService() { }

    // Registers a new user, returning the same response shape as login on success.
    // Throws an IllegalArgumentException if the phone number is already taken.
    public static Map<String, Object> register(String name, String phone, String password,
                                               String role, Integer spokeId, Integer hubId)
            throws SQLException {
        if (isPhoneInUse(phone)) {
            throw new IllegalArgumentException("Phone number is already registered.");
        }

        String passwordHash = hashPassword(password);

        String sql = "INSERT INTO users (name, phone, password_hash, role, spoke_id, hub_id) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";
        int newUserId;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, phone);
            ps.setString(3, passwordHash);
            ps.setString(4, role);
            if (spokeId == null) {
                ps.setNull(5, java.sql.Types.INTEGER);
            } else {
                ps.setInt(5, spokeId);
            }
            if (hubId == null) {
                ps.setNull(6, java.sql.Types.INTEGER);
            } else {
                ps.setInt(6, hubId);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No user id returned after insert.");
                }
                newUserId = keys.getInt(1);
            }
        }

        String token = createSession(newUserId);
        Map<String, Object> response = new HashMap<>();
        response.put("token",   token);
        response.put("user_id", newUserId);
        response.put("role",    role);
        response.put("hub_id",  hubId);
        response.put("spoke_id", spokeId);
        response.put("name",    name);
        return response;
    }

    // Looks up the user by phone, verifies the password hash, creates a session,
    // and returns the token along with the user's profile fields.
    // Returns null if the phone or password is wrong.
    public static Map<String, Object> login(String phone, String password) throws SQLException {
        Farmer user = findUserByPhone(phone);
        if (user == null) {
            return null;
        }
        String givenHash = hashPassword(password);
        if (!givenHash.equalsIgnoreCase(user.getPasswordHash())) {
            return null;
        }
        String token = createSession(user.getId());
        Map<String, Object> response = new HashMap<>();
        response.put("token",    token);
        response.put("user_id",  user.getId());
        response.put("role",     user.getRole());
        response.put("hub_id",   user.getHubId());
        response.put("spoke_id", user.getSpokeId());
        response.put("name",     user.getName());
        return response;
    }

    // Looks up a session token and returns the associated user profile,
    // or null if the token does not exist.
    public static Map<String, Object> validateToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        String sql = "SELECT s.user_id, u.role, u.hub_id, u.spoke_id, u.name "
                   + "  FROM sessions s "
                   + "  JOIN users u ON u.id = s.user_id "
                   + " WHERE s.id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> data = new HashMap<>();
                data.put("user_id",  rs.getInt("user_id"));
                data.put("role",     rs.getString("role"));
                int hubId = rs.getInt("hub_id");
                data.put("hub_id",   rs.wasNull() ? null : hubId);
                int spokeId = rs.getInt("spoke_id");
                data.put("spoke_id", rs.wasNull() ? null : spokeId);
                data.put("name",     rs.getString("name"));
                return data;
            }
        } catch (SQLException e) {
            System.err.println("validateToken query failed: " + e.getMessage());
            return null;
        }
    }

    // Produces the lowercase hex SHA-256 hash of the given plain password.
    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] raw = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : raw) {
                hex.append(String.format(HEX_BYTE_FORMAT, b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available on this JVM", e);
        }
    }

    // Returns true when the given phone already exists in the users table.
    private static boolean isPhoneInUse(String phone) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE phone = ? LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // Loads a user record by phone number, or null if not found.
    private static Farmer findUserByPhone(String phone) throws SQLException {
        String sql = "SELECT id, name, phone, password_hash, role, spoke_id, hub_id, created_at "
                   + "  FROM users WHERE phone = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Farmer u = new Farmer();
                u.setId(rs.getInt("id"));
                u.setName(rs.getString("name"));
                u.setPhone(rs.getString("phone"));
                u.setPasswordHash(rs.getString("password_hash"));
                u.setRole(rs.getString("role"));
                int spoke = rs.getInt("spoke_id");
                u.setSpokeId(rs.wasNull() ? null : spoke);
                int hub = rs.getInt("hub_id");
                u.setHubId(rs.wasNull() ? null : hub);
                u.setCreatedAt(rs.getTimestamp("created_at"));
                return u;
            }
        }
    }

    // Creates a new session row and returns the random UUID-shaped token.
    private static String createSession(int userId) throws SQLException {
        String token = UUID.randomUUID().toString().replace("-", "");
        String sql = "INSERT INTO sessions (id, user_id) VALUES (?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
        return token;
    }
}
