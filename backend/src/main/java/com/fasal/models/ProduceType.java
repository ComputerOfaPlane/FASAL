package com.fasal.models;

// ProduceType is the master record describing a kind of produce - what it's
// called, how fast it spoils (lambda), and which unit it is measured in.
public class ProduceType {

    private int id;
    private String name;
    private double lambdaValue;
    private String unit;

    // No-argument constructor used when reading rows from the database.
    public ProduceType() { }

    // Returns the database ID of this produce type.
    public int getId() { return id; }
    // Sets the database ID of this produce type.
    public void setId(int id) { this.id = id; }

    // Returns the produce's human-friendly name (e.g. "tomato").
    public String getName() { return name; }
    // Sets the produce's human-friendly name.
    public void setName(String name) { this.name = name; }

    // Returns lambda, the decay rate per day in Q(t) = e^(-lambda * t).
    public double getLambdaValue() { return lambdaValue; }
    // Sets lambda, the decay rate per day.
    public void setLambdaValue(double lambdaValue) { this.lambdaValue = lambdaValue; }

    // Returns the unit of measure ("kg", "liters", etc.).
    public String getUnit() { return unit; }
    // Sets the unit of measure.
    public void setUnit(String unit) { this.unit = unit; }
}
