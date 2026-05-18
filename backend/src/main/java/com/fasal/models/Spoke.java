package com.fasal.models;

// A Spoke is a smaller village or town that funnels its produce up to a parent Hub.
// Farmers register against one spoke.
public class Spoke {

    private int id;
    private String name;
    private int hubId;
    private double latitude;
    private double longitude;

    // No-argument constructor so we can build the object piece by piece.
    public Spoke() { }

    // Returns the database ID of this spoke.
    public int getId() { return id; }
    // Sets the database ID of this spoke.
    public void setId(int id) { this.id = id; }

    // Returns the village/town name.
    public String getName() { return name; }
    // Sets the village/town name.
    public void setName(String name) { this.name = name; }

    // Returns the ID of the hub this spoke reports to.
    public int getHubId() { return hubId; }
    // Sets the ID of the hub this spoke reports to.
    public void setHubId(int hubId) { this.hubId = hubId; }

    // Returns the spoke's latitude in decimal degrees.
    public double getLatitude() { return latitude; }
    // Sets the spoke's latitude in decimal degrees.
    public void setLatitude(double latitude) { this.latitude = latitude; }

    // Returns the spoke's longitude in decimal degrees.
    public double getLongitude() { return longitude; }
    // Sets the spoke's longitude in decimal degrees.
    public void setLongitude(double longitude) { this.longitude = longitude; }
}
