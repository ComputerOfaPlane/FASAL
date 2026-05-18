package com.fasal.models;

// Vehicle is one truck in the fleet. It has a capacity, lives at a current
// hub, and is either IDLE (free to dispatch) or IN_TRANSIT (on a route).
public class Vehicle {

    private int id;
    private String name;
    private double capacityKg;
    private int currentHubId;
    private String status;

    // No-argument constructor used by the data access layer.
    public Vehicle() { }

    // Returns the database ID of this vehicle.
    public int getId() { return id; }
    // Sets the database ID of this vehicle.
    public void setId(int id) { this.id = id; }

    // Returns the truck's display name (e.g. "Delhi-Truck-1").
    public String getName() { return name; }
    // Sets the truck's display name.
    public void setName(String name) { this.name = name; }

    // Returns how many kilograms the truck can carry in one trip.
    public double getCapacityKg() { return capacityKg; }
    // Sets how many kilograms the truck can carry.
    public void setCapacityKg(double capacityKg) { this.capacityKg = capacityKg; }

    // Returns the ID of the hub the truck is currently parked at.
    public int getCurrentHubId() { return currentHubId; }
    // Sets the ID of the hub the truck is currently parked at.
    public void setCurrentHubId(int currentHubId) { this.currentHubId = currentHubId; }

    // Returns the operational status: IDLE or IN_TRANSIT.
    public String getStatus() { return status; }
    // Sets the operational status.
    public void setStatus(String status) { this.status = status; }
}
