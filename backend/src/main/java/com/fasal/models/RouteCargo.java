package com.fasal.models;

// RouteCargo is one piece of cargo travelling on a Route - what produce,
// how much, and where it is going.
public class RouteCargo {

    private int id;
    private int routeId;
    private int produceTypeId;
    private double quantityKg;
    private int sourceHubId;
    private int destinationHubId;

    // Joined-in field: the produce name for display.
    private String produceName;
    // Joined-in field: the destination hub name for display.
    private String destinationHubName;
    // Joined-in field: the source hub name for display.
    private String sourceHubName;

    // No-argument constructor used when reading rows from the database.
    public RouteCargo() { }

    // Returns the database ID of this cargo row.
    public int getId() { return id; }
    // Sets the database ID of this cargo row.
    public void setId(int id) { this.id = id; }

    // Returns the ID of the parent route.
    public int getRouteId() { return routeId; }
    // Sets the ID of the parent route.
    public void setRouteId(int routeId) { this.routeId = routeId; }

    // Returns the produce type being carried.
    public int getProduceTypeId() { return produceTypeId; }
    // Sets the produce type being carried.
    public void setProduceTypeId(int produceTypeId) { this.produceTypeId = produceTypeId; }

    // Returns how many kilograms of cargo this represents.
    public double getQuantityKg() { return quantityKg; }
    // Sets how many kilograms of cargo this represents.
    public void setQuantityKg(double quantityKg) { this.quantityKg = quantityKg; }

    // Returns the hub the cargo is leaving from.
    public int getSourceHubId() { return sourceHubId; }
    // Sets the hub the cargo is leaving from.
    public void setSourceHubId(int sourceHubId) { this.sourceHubId = sourceHubId; }

    // Returns the hub the cargo is heading to.
    public int getDestinationHubId() { return destinationHubId; }
    // Sets the hub the cargo is heading to.
    public void setDestinationHubId(int destinationHubId) { this.destinationHubId = destinationHubId; }

    // Returns the produce name joined in for display.
    public String getProduceName() { return produceName; }
    // Sets the produce name joined in for display.
    public void setProduceName(String produceName) { this.produceName = produceName; }

    // Returns the destination hub's display name.
    public String getDestinationHubName() { return destinationHubName; }
    // Sets the destination hub's display name.
    public void setDestinationHubName(String destinationHubName) { this.destinationHubName = destinationHubName; }

    // Returns the source hub's display name.
    public String getSourceHubName() { return sourceHubName; }
    // Sets the source hub's display name.
    public void setSourceHubName(String sourceHubName) { this.sourceHubName = sourceHubName; }
}
