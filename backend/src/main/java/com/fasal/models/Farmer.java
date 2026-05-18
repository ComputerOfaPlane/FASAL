package com.fasal.models;

import java.sql.Timestamp;

// Farmer represents one row in the users table. Although named "Farmer" for the
// project's farming theme, the same class is used for hub admins and the super
// admin because every user has the same set of fields.
// The role field tells you which kind of user this actually is.
public class Farmer {

    private int id;
    private String name;
    private String phone;
    private String passwordHash;
    private String role;
    // spokeId is only filled in when role == "FARMER".
    private Integer spokeId;
    // hubId is only filled in when role == "HUB_ADMIN".
    private Integer hubId;
    private Timestamp createdAt;

    // No-argument constructor used everywhere we build a Farmer from a database row.
    public Farmer() { }

    // Returns the database ID of this user.
    public int getId() { return id; }
    // Sets the database ID of this user.
    public void setId(int id) { this.id = id; }

    // Returns the user's full name.
    public String getName() { return name; }
    // Sets the user's full name.
    public void setName(String name) { this.name = name; }

    // Returns the phone number used as the unique login handle.
    public String getPhone() { return phone; }
    // Sets the phone number used for login.
    public void setPhone(String phone) { this.phone = phone; }

    // Returns the SHA-256 hex hash of the user's password.
    public String getPasswordHash() { return passwordHash; }
    // Sets the SHA-256 hex hash of the user's password.
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    // Returns the user's role: "FARMER", "HUB_ADMIN" or "SUPER_ADMIN".
    public String getRole() { return role; }
    // Sets the user's role string.
    public void setRole(String role) { this.role = role; }

    // Returns the spoke ID this farmer belongs to, or null for non-farmers.
    public Integer getSpokeId() { return spokeId; }
    // Sets the spoke ID this farmer belongs to.
    public void setSpokeId(Integer spokeId) { this.spokeId = spokeId; }

    // Returns the hub ID this hub admin manages, or null for non-admins.
    public Integer getHubId() { return hubId; }
    // Sets the hub ID this hub admin manages.
    public void setHubId(Integer hubId) { this.hubId = hubId; }

    // Returns the timestamp when the account was created.
    public Timestamp getCreatedAt() { return createdAt; }
    // Sets the timestamp when the account was created.
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
