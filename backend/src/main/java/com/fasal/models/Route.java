package com.fasal.models;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

// Route represents one delivery plan assigned to one vehicle - the truck will
// visit a list of stops and drop off cargo at each one.
public class Route {

    private int id;
    private int vehicleId;
    private Timestamp createdAt;
    private String status;
    private boolean requiresColdStorage;

    // Joined-in field: the truck's display name.
    private String vehicleName;

    // Convenience lists used when returning a Route nested with its stops and cargo.
    private List<RouteStop> stops = new ArrayList<>();
    private List<RouteCargo> cargo = new ArrayList<>();

    // No-argument constructor used when reading rows from the database.
    public Route() { }

    // Returns the database ID of this route.
    public int getId() { return id; }
    // Sets the database ID of this route.
    public void setId(int id) { this.id = id; }

    // Returns the ID of the vehicle assigned to this route.
    public int getVehicleId() { return vehicleId; }
    // Sets the ID of the vehicle assigned to this route.
    public void setVehicleId(int vehicleId) { this.vehicleId = vehicleId; }

    // Returns when the route was created.
    public Timestamp getCreatedAt() { return createdAt; }
    // Sets when the route was created.
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    // Returns the route's lifecycle state: PLANNED, ACTIVE or COMPLETED.
    public String getStatus() { return status; }
    // Sets the route's lifecycle state.
    public void setStatus(String status) { this.status = status; }

    // Returns true when refrigerated transport is required.
    public boolean isRequiresColdStorage() { return requiresColdStorage; }
    // Sets whether refrigerated transport is required.
    public void setRequiresColdStorage(boolean requiresColdStorage) { this.requiresColdStorage = requiresColdStorage; }

    // Returns the vehicle name joined in for display.
    public String getVehicleName() { return vehicleName; }
    // Sets the vehicle name joined in for display.
    public void setVehicleName(String vehicleName) { this.vehicleName = vehicleName; }

    // Returns the ordered stops that belong to this route.
    public List<RouteStop> getStops() { return stops; }
    // Sets the ordered stops that belong to this route.
    public void setStops(List<RouteStop> stops) { this.stops = stops; }

    // Returns the cargo items carried on this route.
    public List<RouteCargo> getCargo() { return cargo; }
    // Sets the cargo items carried on this route.
    public void setCargo(List<RouteCargo> cargo) { this.cargo = cargo; }
}
