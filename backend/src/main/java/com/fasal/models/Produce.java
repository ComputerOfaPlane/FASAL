package com.fasal.models;

import java.sql.Timestamp;
import java.time.LocalDate;

// Produce represents one row in produce_listings - a single batch of produce
// declared by a farmer when they harvest. It is NOT the same as a produce TYPE
// (that is ProduceType.java).
public class Produce {

    private int id;
    private int farmerId;
    private int produceTypeId;
    private double quantityKg;
    private LocalDate harvestDate;
    private int hubId;
    private String status;
    private Timestamp createdAt;

    // Friendly name of the produce, filled in from a join with produce_types.
    private String produceName;
    // Decay rate of the produce, filled in from a join with produce_types.
    private double lambdaValue;
    // The current freshness Q(t), computed in Java when we read the row.
    private double currentQuality;

    // No-argument constructor used when assembling a Produce from a query result.
    public Produce() { }

    // Returns the database ID of this listing.
    public int getId() { return id; }
    // Sets the database ID of this listing.
    public void setId(int id) { this.id = id; }

    // Returns the user ID of the farmer who created this listing.
    public int getFarmerId() { return farmerId; }
    // Sets the user ID of the farmer who created this listing.
    public void setFarmerId(int farmerId) { this.farmerId = farmerId; }

    // Returns the produce type ID this listing is for.
    public int getProduceTypeId() { return produceTypeId; }
    // Sets the produce type ID this listing is for.
    public void setProduceTypeId(int produceTypeId) { this.produceTypeId = produceTypeId; }

    // Returns how many kilograms were harvested.
    public double getQuantityKg() { return quantityKg; }
    // Sets how many kilograms were harvested.
    public void setQuantityKg(double quantityKg) { this.quantityKg = quantityKg; }

    // Returns the date the produce was picked.
    public LocalDate getHarvestDate() { return harvestDate; }
    // Sets the date the produce was picked.
    public void setHarvestDate(LocalDate harvestDate) { this.harvestDate = harvestDate; }

    // Returns the hub the produce was delivered to.
    public int getHubId() { return hubId; }
    // Sets the hub the produce was delivered to.
    public void setHubId(int hubId) { this.hubId = hubId; }

    // Returns the listing status: PENDING, IN_TRANSIT or DELIVERED.
    public String getStatus() { return status; }
    // Sets the listing status.
    public void setStatus(String status) { this.status = status; }

    // Returns the timestamp when this listing was created.
    public Timestamp getCreatedAt() { return createdAt; }
    // Sets the timestamp when this listing was created.
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    // Returns the produce name (e.g. "tomato") joined in for display.
    public String getProduceName() { return produceName; }
    // Sets the produce name joined in for display.
    public void setProduceName(String produceName) { this.produceName = produceName; }

    // Returns the decay rate lambda for the produce type.
    public double getLambdaValue() { return lambdaValue; }
    // Sets the decay rate lambda for the produce type.
    public void setLambdaValue(double lambdaValue) { this.lambdaValue = lambdaValue; }

    // Returns the freshness value Q(t) computed when this listing was loaded.
    public double getCurrentQuality() { return currentQuality; }
    // Sets the freshness value Q(t).
    public void setCurrentQuality(double currentQuality) { this.currentQuality = currentQuality; }
}
