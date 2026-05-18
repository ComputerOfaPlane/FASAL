package com.fasal.models;

// A Hub is one of the big city centres where produce is collected, stored,
// and sent on to other hubs. Each Hub row in the database becomes one Hub object.
public class Hub {

    private int id;
    private String name;
    private String city;
    private double latitude;
    private double longitude;

    // No-argument constructor so frameworks and our own code can create a blank Hub.
    public Hub() { }

    // Returns the database ID of this hub.
    public int getId() { return id; }
    // Sets the database ID of this hub.
    public void setId(int id) { this.id = id; }

    // Returns the human-friendly name (e.g. "Delhi Hub").
    public String getName() { return name; }
    // Sets the human-friendly name.
    public void setName(String name) { this.name = name; }

    // Returns the city this hub sits in.
    public String getCity() { return city; }
    // Sets the city this hub sits in.
    public void setCity(String city) { this.city = city; }

    // Returns the hub's latitude in decimal degrees (north/south position).
    public double getLatitude() { return latitude; }
    // Sets the hub's latitude in decimal degrees.
    public void setLatitude(double latitude) { this.latitude = latitude; }

    // Returns the hub's longitude in decimal degrees (east/west position).
    public double getLongitude() { return longitude; }
    // Sets the hub's longitude in decimal degrees.
    public void setLongitude(double longitude) { this.longitude = longitude; }
}
