package com.fasal.models;

import java.sql.Timestamp;

// RouteStop is one stop along a Route - which hub to visit and in what order.
public class RouteStop {

    private int id;
    private int routeId;
    private int hubId;
    private int stopOrder;
    private Timestamp arrivedAt;

    // Joined-in field: the hub's display name.
    private String hubName;

    // No-argument constructor used when reading rows from the database.
    public RouteStop() { }

    // Returns the database ID of this stop row.
    public int getId() { return id; }
    // Sets the database ID of this stop row.
    public void setId(int id) { this.id = id; }

    // Returns the ID of the parent route.
    public int getRouteId() { return routeId; }
    // Sets the ID of the parent route.
    public void setRouteId(int routeId) { this.routeId = routeId; }

    // Returns the hub to be visited at this stop.
    public int getHubId() { return hubId; }
    // Sets the hub to be visited at this stop.
    public void setHubId(int hubId) { this.hubId = hubId; }

    // Returns the position of this stop in the route (1 = first stop).
    public int getStopOrder() { return stopOrder; }
    // Sets the position of this stop in the route.
    public void setStopOrder(int stopOrder) { this.stopOrder = stopOrder; }

    // Returns when the truck actually arrived at this stop, or null if not yet.
    public Timestamp getArrivedAt() { return arrivedAt; }
    // Sets when the truck actually arrived at this stop.
    public void setArrivedAt(Timestamp arrivedAt) { this.arrivedAt = arrivedAt; }

    // Returns the hub name joined in for display.
    public String getHubName() { return hubName; }
    // Sets the hub name joined in for display.
    public void setHubName(String hubName) { this.hubName = hubName; }
}
