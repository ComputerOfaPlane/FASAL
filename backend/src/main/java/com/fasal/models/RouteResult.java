package com.fasal.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// RouteResult is the full output of one run of the routing engine. It is what
// the API returns after POST /api/routing/run is called.
public class RouteResult {

    // The database ID of the route we created (0 if we did not save anything).
    private int routeId;
    // The vehicle picked to run this route.
    private String vehicleName;
    // The ID of the picked vehicle, useful for follow-up API calls.
    private int vehicleId;
    // The ordered stops the truck will visit, source hub first.
    private List<RouteStop> stops = new ArrayList<>();
    // The cargo loaded onto the truck.
    private List<RouteCargo> cargo = new ArrayList<>();
    // True when at least one cargo item needs refrigeration to stay fresh.
    private boolean requiresColdStorage;
    // Map from stop_order to the freshness Q(t) of the cargo arriving at that stop.
    private Map<Integer, Double> qualityAtEachStop = new HashMap<>();
    // A friendly sentence that summarises the whole result for humans.
    private String humanReadableSummary;
    // A reason string explaining why cold storage was flagged, if it was.
    private String coldStorageReason;

    // No-argument constructor so callers can build the result piece by piece.
    public RouteResult() { }

    // Returns the database ID of the saved route.
    public int getRouteId() { return routeId; }
    // Sets the database ID of the saved route.
    public void setRouteId(int routeId) { this.routeId = routeId; }

    // Returns the name of the truck running this route.
    public String getVehicleName() { return vehicleName; }
    // Sets the name of the truck running this route.
    public void setVehicleName(String vehicleName) { this.vehicleName = vehicleName; }

    // Returns the database ID of the truck running this route.
    public int getVehicleId() { return vehicleId; }
    // Sets the database ID of the truck running this route.
    public void setVehicleId(int vehicleId) { this.vehicleId = vehicleId; }

    // Returns the ordered list of stops.
    public List<RouteStop> getStops() { return stops; }
    // Sets the ordered list of stops.
    public void setStops(List<RouteStop> stops) { this.stops = stops; }

    // Returns the list of cargo items.
    public List<RouteCargo> getCargo() { return cargo; }
    // Sets the list of cargo items.
    public void setCargo(List<RouteCargo> cargo) { this.cargo = cargo; }

    // Returns true if the route requires a refrigerated truck.
    public boolean isRequiresColdStorage() { return requiresColdStorage; }
    // Sets whether the route requires a refrigerated truck.
    public void setRequiresColdStorage(boolean requiresColdStorage) { this.requiresColdStorage = requiresColdStorage; }

    // Returns the freshness Q(t) at each stop.
    public Map<Integer, Double> getQualityAtEachStop() { return qualityAtEachStop; }
    // Sets the freshness Q(t) at each stop.
    public void setQualityAtEachStop(Map<Integer, Double> qualityAtEachStop) { this.qualityAtEachStop = qualityAtEachStop; }

    // Returns the human-readable summary sentence.
    public String getHumanReadableSummary() { return humanReadableSummary; }
    // Sets the human-readable summary sentence.
    public void setHumanReadableSummary(String humanReadableSummary) { this.humanReadableSummary = humanReadableSummary; }

    // Returns the reason cold storage was flagged.
    public String getColdStorageReason() { return coldStorageReason; }
    // Sets the reason cold storage was flagged.
    public void setColdStorageReason(String coldStorageReason) { this.coldStorageReason = coldStorageReason; }
}
