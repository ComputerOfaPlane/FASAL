package com.fasal.models;

import java.sql.Timestamp;
import java.time.LocalDate;

// Inventory says how much of a given produce type is sitting at a given hub
// right now, and what the average harvest date of that stock is (used to compute
// freshness).
public class Inventory {

    private int id;
    private int hubId;
    private int produceTypeId;
    private double quantityKg;
    private LocalDate avgHarvestDate;
    private Timestamp lastUpdated;

    // Joined-in field: the produce name, e.g. "tomato".
    private String produceName;
    // Joined-in field: the decay rate of the produce.
    private double lambdaValue;
    // Computed in Java when we read the row: the current freshness Q(t).
    private double currentQuality;

    // No-argument constructor used when reading rows from the database.
    public Inventory() { }

    // Returns the database ID of this inventory row.
    public int getId() { return id; }
    // Sets the database ID of this inventory row.
    public void setId(int id) { this.id = id; }

    // Returns the hub this stock is stored at.
    public int getHubId() { return hubId; }
    // Sets the hub this stock is stored at.
    public void setHubId(int hubId) { this.hubId = hubId; }

    // Returns the produce type of this stock.
    public int getProduceTypeId() { return produceTypeId; }
    // Sets the produce type of this stock.
    public void setProduceTypeId(int produceTypeId) { this.produceTypeId = produceTypeId; }

    // Returns the quantity of produce in kilograms.
    public double getQuantityKg() { return quantityKg; }
    // Sets the quantity of produce in kilograms.
    public void setQuantityKg(double quantityKg) { this.quantityKg = quantityKg; }

    // Returns the average harvest date of this stock.
    public LocalDate getAvgHarvestDate() { return avgHarvestDate; }
    // Sets the average harvest date of this stock.
    public void setAvgHarvestDate(LocalDate avgHarvestDate) { this.avgHarvestDate = avgHarvestDate; }

    // Returns the timestamp this row was last touched.
    public Timestamp getLastUpdated() { return lastUpdated; }
    // Sets the timestamp this row was last touched.
    public void setLastUpdated(Timestamp lastUpdated) { this.lastUpdated = lastUpdated; }

    // Returns the produce name joined in for display.
    public String getProduceName() { return produceName; }
    // Sets the produce name joined in for display.
    public void setProduceName(String produceName) { this.produceName = produceName; }

    // Returns the lambda value of the produce type.
    public double getLambdaValue() { return lambdaValue; }
    // Sets the lambda value of the produce type.
    public void setLambdaValue(double lambdaValue) { this.lambdaValue = lambdaValue; }

    // Returns the freshness Q(t) computed when this row was loaded.
    public double getCurrentQuality() { return currentQuality; }
    // Sets the freshness Q(t).
    public void setCurrentQuality(double currentQuality) { this.currentQuality = currentQuality; }
}
