package com.fasal.models;

import java.sql.Timestamp;

// Demand says: "this hub needs N kg of this produce, and won't accept anything
// below this freshness threshold." The routing engine uses this to decide what
// to ship and to which hub.
public class Demand {

    private int id;
    private int hubId;
    private int produceTypeId;
    private double requiredQuantityKg;
    private double minQualityThreshold;
    private Timestamp createdAt;

    // Joined-in field: the produce name for display.
    private String produceName;
    // Joined-in field: the decay rate of the produce.
    private double lambdaValue;

    // No-argument constructor used when reading rows from the database.
    public Demand() { }

    // Returns the database ID of this demand row.
    public int getId() { return id; }
    // Sets the database ID of this demand row.
    public void setId(int id) { this.id = id; }

    // Returns the hub that wants the produce.
    public int getHubId() { return hubId; }
    // Sets the hub that wants the produce.
    public void setHubId(int hubId) { this.hubId = hubId; }

    // Returns the produce type the hub wants.
    public int getProduceTypeId() { return produceTypeId; }
    // Sets the produce type the hub wants.
    public void setProduceTypeId(int produceTypeId) { this.produceTypeId = produceTypeId; }

    // Returns how many kilograms the hub needs.
    public double getRequiredQuantityKg() { return requiredQuantityKg; }
    // Sets how many kilograms the hub needs.
    public void setRequiredQuantityKg(double requiredQuantityKg) { this.requiredQuantityKg = requiredQuantityKg; }

    // Returns the lowest Q(t) value the buyer will accept on arrival.
    public double getMinQualityThreshold() { return minQualityThreshold; }
    // Sets the lowest Q(t) value the buyer will accept on arrival.
    public void setMinQualityThreshold(double minQualityThreshold) { this.minQualityThreshold = minQualityThreshold; }

    // Returns when the demand row was recorded.
    public Timestamp getCreatedAt() { return createdAt; }
    // Sets when the demand row was recorded.
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    // Returns the produce name joined in for display.
    public String getProduceName() { return produceName; }
    // Sets the produce name joined in for display.
    public void setProduceName(String produceName) { this.produceName = produceName; }

    // Returns the lambda value of the produce type.
    public double getLambdaValue() { return lambdaValue; }
    // Sets the lambda value of the produce type.
    public void setLambdaValue(double lambdaValue) { this.lambdaValue = lambdaValue; }
}
