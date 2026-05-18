package com.fasal.algorithm;

import com.fasal.db.DatabaseConnection;
import com.fasal.models.RouteCargo;
import com.fasal.models.RouteResult;
import com.fasal.models.RouteStop;
import com.fasal.models.Vehicle;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// The RoutingEngine is the brain of FASAL. Given one source hub, it figures
// out: what we have too much of, which other hubs want it, in what order
// to visit them, whether the cargo will stay fresh, and saves the plan to
// the database. The work is split into 7 clearly-named steps to make it
// easy to follow.
public class RoutingEngine {

    // Status code used for routes we have just planned but have not yet started.
    private static final String STATUS_PLANNED = "PLANNED";
    // Status code used to mark a vehicle as being on the road.
    private static final String STATUS_IN_TRANSIT = "IN_TRANSIT";
    // Status code used to look up a free truck.
    private static final String STATUS_IDLE = "IDLE";
    // Used so we always include the source hub as the first entry in the route.
    private static final int FIRST_STOP_ORDER = 1;
    // The freshness we report at the source hub (the cargo is just being loaded).
    private static final double SOURCE_HUB_QUALITY = QualityCalculator.MAX_QUALITY;
    // Travel time between a hub and itself is always zero.
    private static final double SELF_TRAVEL_HOURS = 0.0;

    // One surplus entry per produce type the source hub has more of than it needs.
    private static class Surplus {
        int produceTypeId;
        String produceName;
        double quantityKg;
        LocalDate avgHarvestDate;
        double lambdaValue;
    }

    // One MatchedDemand entry per other-hub demand that lines up with our surplus.
    private static class MatchedDemand {
        int demandId;
        int destinationHubId;
        String destinationHubName;
        int produceTypeId;
        String produceName;
        double requiredQuantityKg;
        double minQualityThreshold;
        double travelTimeHoursFromSource;
        double distanceKmFromSource;
        double projectedQualityFromSource;
        LocalDate avgHarvestDate;
        double lambdaValue;
    }

    // Plans a route starting from the given source hub. Returns a RouteResult
    // describing the planned trip, or a "nothing to do" result if there is no
    // matching demand or no available truck.
    public RouteResult runRouting(int sourceHubId) {
        // Step 1: figure out what this hub has too much of.
        List<Surplus> surpluses = calculateSurplus(sourceHubId);
        if (surpluses.isEmpty()) {
            return buildEmptyResult("This hub has no surplus produce to ship.");
        }

        // Step 2: find other hubs that need this produce, where it will still be fresh.
        List<MatchedDemand> matched = findMatchingDemands(surpluses, sourceHubId);
        if (matched.isEmpty()) {
            return buildEmptyResult("No matching demand was found for the available surplus.");
        }

        // Step 3: put the most-perishable demands first.
        List<MatchedDemand> prioritised = prioritiseDemands(matched);

        // We also need a vehicle to run the route on.
        Vehicle vehicle = pickIdleVehicle(sourceHubId);
        if (vehicle == null) {
            return buildEmptyResult("No idle vehicle is currently available at this hub.");
        }

        // Step 4: build the actual stop/cargo lists using a greedy nearest-neighbour walk.
        List<RouteStop> stops = new ArrayList<>();
        List<RouteCargo> cargo = new ArrayList<>();
        Map<Integer, Double> qualityAtEachStop = new HashMap<>();
        buildRoute(surpluses, prioritised, sourceHubId, vehicle.getCapacityKg(),
                   stops, cargo, qualityAtEachStop);

        if (cargo.isEmpty()) {
            return buildEmptyResult("Vehicle capacity could not be matched to any demand.");
        }

        // Step 5: decide whether the truck needs refrigeration.
        StringBuilder coldReason = new StringBuilder();
        boolean requiresColdStorage = evaluateColdStorage(stops, cargo, prioritised, sourceHubId, coldReason);

        // Step 6: save the route to the database.
        int routeId = persistRoute(vehicle, stops, cargo, requiresColdStorage);

        // Step 7: produce the friendly output object the API returns to the frontend.
        return buildResult(routeId, vehicle, stops, cargo, qualityAtEachStop,
                           requiresColdStorage, coldReason.toString(), sourceHubId);
    }

    // Step 1 - what does this hub have too much of?
    // We find what this hub has more of than it needs locally.
    private List<Surplus> calculateSurplus(int sourceHubId) {
        List<Surplus> result = new ArrayList<>();

        // First load the inventory at the source hub.
        Map<Integer, Surplus> stockByProduceType = new HashMap<>();
        String inventorySql =
            "SELECT i.produce_type_id, i.quantity_kg, i.avg_harvest_date, "
          + "       pt.name, pt.lambda_value "
          + "  FROM inventory i "
          + "  JOIN produce_types pt ON pt.id = i.produce_type_id "
          + " WHERE i.hub_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(inventorySql)) {
            ps.setInt(1, sourceHubId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Surplus s = new Surplus();
                    s.produceTypeId = rs.getInt("produce_type_id");
                    s.quantityKg = rs.getDouble("quantity_kg");
                    s.avgHarvestDate = rs.getDate("avg_harvest_date").toLocalDate();
                    s.produceName = rs.getString("name");
                    s.lambdaValue = rs.getDouble("lambda_value");
                    stockByProduceType.put(s.produceTypeId, s);
                }
            }
        } catch (SQLException e) {
            System.err.println("calculateSurplus inventory query failed: " + e.getMessage());
            return result;
        }

        // Now subtract local demand from the stock to compute the surplus.
        String demandSql =
            "SELECT produce_type_id, SUM(required_quantity_kg) AS total_required "
          + "  FROM demand "
          + " WHERE hub_id = ? "
          + " GROUP BY produce_type_id";

        Map<Integer, Double> localDemand = new HashMap<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(demandSql)) {
            ps.setInt(1, sourceHubId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    localDemand.put(rs.getInt("produce_type_id"),
                                    rs.getDouble("total_required"));
                }
            }
        } catch (SQLException e) {
            System.err.println("calculateSurplus demand query failed: " + e.getMessage());
            return result;
        }

        for (Surplus s : stockByProduceType.values()) {
            double localNeed = localDemand.getOrDefault(s.produceTypeId, 0.0);
            double leftover = s.quantityKg - localNeed;
            if (leftover > 0.0) {
                s.quantityKg = leftover;
                result.add(s);
            }
        }
        return result;
    }

    // Step 2 - which other hubs need what we have, and will it still be fresh on arrival?
    // We find which other hubs need what we have, and check if it will still
    // be good quality when it gets there.
    private List<MatchedDemand> findMatchingDemands(List<Surplus> surpluses, int sourceHubId) {
        List<MatchedDemand> matched = new ArrayList<>();
        if (surpluses.isEmpty()) {
            return matched;
        }

        // Build a lookup of source-hub surpluses for quick membership tests.
        Map<Integer, Surplus> surplusByProduceType = new HashMap<>();
        for (Surplus s : surpluses) {
            surplusByProduceType.put(s.produceTypeId, s);
        }

        // Query every demand row at any hub other than the source.
        String sql =
            "SELECT d.id, d.hub_id, d.produce_type_id, d.required_quantity_kg, "
          + "       d.min_quality_threshold, pt.name AS produce_name, "
          + "       pt.lambda_value, h.name AS hub_name, "
          + "       dist.travel_time_hours, dist.distance_km "
          + "  FROM demand d "
          + "  JOIN produce_types pt ON pt.id = d.produce_type_id "
          + "  JOIN hubs h           ON h.id  = d.hub_id "
          + "  JOIN hub_distances dist ON dist.hub_id_from = ? AND dist.hub_id_to = d.hub_id "
          + " WHERE d.hub_id <> ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sourceHubId);
            ps.setInt(2, sourceHubId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int produceTypeId = rs.getInt("produce_type_id");
                    Surplus surplus = surplusByProduceType.get(produceTypeId);
                    if (surplus == null) {
                        continue;
                    }

                    double travelHours = rs.getDouble("travel_time_hours");
                    double projectedQ = QualityCalculator.calculateQualityAtArrival(
                        surplus.lambdaValue, surplus.avgHarvestDate, travelHours);

                    double minQ = rs.getDouble("min_quality_threshold");
                    if (projectedQ < minQ) {
                        continue;
                    }

                    MatchedDemand md = new MatchedDemand();
                    md.demandId = rs.getInt("id");
                    md.destinationHubId = rs.getInt("hub_id");
                    md.destinationHubName = rs.getString("hub_name");
                    md.produceTypeId = produceTypeId;
                    md.produceName = rs.getString("produce_name");
                    md.requiredQuantityKg = rs.getDouble("required_quantity_kg");
                    md.minQualityThreshold = minQ;
                    md.travelTimeHoursFromSource = travelHours;
                    md.distanceKmFromSource = rs.getDouble("distance_km");
                    md.projectedQualityFromSource = projectedQ;
                    md.avgHarvestDate = surplus.avgHarvestDate;
                    md.lambdaValue = surplus.lambdaValue;
                    matched.add(md);
                }
            }
        } catch (SQLException e) {
            System.err.println("findMatchingDemands query failed: " + e.getMessage());
        }
        return matched;
    }

    // Step 3 - sort matches with most-perishable first.
    // We sort by how fast the produce spoils - most urgent deliveries first.
    private List<MatchedDemand> prioritiseDemands(List<MatchedDemand> matchedDemands) {
        List<MatchedDemand> sorted = new ArrayList<>(matchedDemands);
        sorted.sort(Comparator.comparingDouble((MatchedDemand m) -> m.lambdaValue).reversed());
        return sorted;
    }

    // Step 4 - build the actual route greedily.
    // We plan the delivery route greedily: always go to the nearest hub next.
    // This is not perfect but it is fast and practical.
    private void buildRoute(List<Surplus> surpluses, List<MatchedDemand> prioritisedDemands,
                            int sourceHubId, double vehicleCapacityKg,
                            List<RouteStop> outStops, List<RouteCargo> outCargo,
                            Map<Integer, Double> outQualityAtStop) {

        // Track how much of each produce we have left to give out as we plan.
        Map<Integer, Double> remainingSurplus = new HashMap<>();
        for (Surplus s : surpluses) {
            remainingSurplus.put(s.produceTypeId, s.quantityKg);
        }

        // The first stop is always the source hub - that is where the truck loads up.
        double remainingCapacityKg = vehicleCapacityKg;
        int currentHubId = sourceHubId;
        double cumulativeHours = 0.0;
        int nextStopOrder = FIRST_STOP_ORDER;

        RouteStop sourceStop = new RouteStop();
        sourceStop.setHubId(sourceHubId);
        sourceStop.setStopOrder(nextStopOrder);
        outStops.add(sourceStop);
        outQualityAtStop.put(nextStopOrder, SOURCE_HUB_QUALITY);
        nextStopOrder++;

        // Working copy of demands so we can remove ones we have served.
        List<MatchedDemand> pending = new ArrayList<>(prioritisedDemands);

        while (!pending.isEmpty() && remainingCapacityKg > 0.0) {
            // Pick the pending demand with the shortest hop from where the truck is now.
            MatchedDemand nearest = findNearestPendingDemand(currentHubId, pending);
            if (nearest == null) {
                break;
            }

            // How much of this produce type do we still have to ship?
            double available = remainingSurplus.getOrDefault(nearest.produceTypeId, 0.0);
            if (available <= 0.0) {
                pending.remove(nearest);
                continue;
            }

            // Load as much as fits: limited by the demand size, what is left of the surplus,
            // and what is left of the truck's capacity.
            double assignedKg = Math.min(nearest.requiredQuantityKg,
                                         Math.min(available, remainingCapacityKg));
            if (assignedKg <= 0.0) {
                pending.remove(nearest);
                continue;
            }

            // Compute the truck's total travel time after this leg, then the freshness
            // of the cargo when it arrives at this stop.
            double legHours = lookupTravelHours(currentHubId, nearest.destinationHubId);
            cumulativeHours += legHours;
            double qualityAtArrival = QualityCalculator.calculateQualityAtArrival(
                nearest.lambdaValue, nearest.avgHarvestDate, cumulativeHours);

            // Record the stop and the cargo for this leg.
            RouteStop stop = new RouteStop();
            stop.setHubId(nearest.destinationHubId);
            stop.setHubName(nearest.destinationHubName);
            stop.setStopOrder(nextStopOrder);
            outStops.add(stop);
            outQualityAtStop.put(nextStopOrder, qualityAtArrival);

            RouteCargo c = new RouteCargo();
            c.setProduceTypeId(nearest.produceTypeId);
            c.setProduceName(nearest.produceName);
            c.setQuantityKg(assignedKg);
            c.setSourceHubId(sourceHubId);
            c.setDestinationHubId(nearest.destinationHubId);
            c.setDestinationHubName(nearest.destinationHubName);
            outCargo.add(c);

            // Bookkeeping: deduct, advance current position, drop the served demand.
            remainingSurplus.put(nearest.produceTypeId, available - assignedKg);
            remainingCapacityKg -= assignedKg;
            currentHubId = nearest.destinationHubId;
            pending.remove(nearest);
            nextStopOrder++;
        }
    }

    // Helper: from the pending list, find the demand whose hub is closest in
    // travel time to the current position of the truck.
    private MatchedDemand findNearestPendingDemand(int currentHubId, List<MatchedDemand> pending) {
        MatchedDemand best = null;
        double bestHours = Double.POSITIVE_INFINITY;
        for (MatchedDemand md : pending) {
            double hours = lookupTravelHours(currentHubId, md.destinationHubId);
            if (hours < bestHours) {
                bestHours = hours;
                best = md;
            }
        }
        return best;
    }

    // Helper: look up the travel time between two hubs from the hub_distances table.
    // Returns zero when from == to (no travel needed).
    private double lookupTravelHours(int fromHubId, int toHubId) {
        if (fromHubId == toHubId) {
            return SELF_TRAVEL_HOURS;
        }
        String sql = "SELECT travel_time_hours FROM hub_distances WHERE hub_id_from = ? AND hub_id_to = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fromHubId);
            ps.setInt(2, toHubId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("travel_time_hours");
                }
            }
        } catch (SQLException e) {
            System.err.println("lookupTravelHours query failed: " + e.getMessage());
        }
        return Double.POSITIVE_INFINITY;
    }

    // Step 5 - decide whether the truck needs refrigeration.
    // We check if any goods will go bad before reaching their destination.
    // If so, we flag that a refrigerated truck is needed.
    private boolean evaluateColdStorage(List<RouteStop> stops, List<RouteCargo> cargo,
                                         List<MatchedDemand> prioritisedDemands,
                                         int sourceHubId, StringBuilder reasonOut) {
        // We need to know cumulative travel time at each stop to project quality.
        Map<Integer, Double> hoursAtStopOrder = new HashMap<>();
        double running = 0.0;
        int previousHubId = sourceHubId;
        for (RouteStop s : stops) {
            if (s.getStopOrder() == FIRST_STOP_ORDER) {
                hoursAtStopOrder.put(s.getStopOrder(), 0.0);
                continue;
            }
            running += lookupTravelHours(previousHubId, s.getHubId());
            hoursAtStopOrder.put(s.getStopOrder(), running);
            previousHubId = s.getHubId();
        }

        // Map destination hub -> stop_order so we can match cargo to a leg.
        Map<Integer, Integer> stopOrderByHub = new HashMap<>();
        for (RouteStop s : stops) {
            stopOrderByHub.put(s.getHubId(), s.getStopOrder());
        }

        // For each cargo item, check if it will arrive below the demand's threshold.
        boolean coldNeeded = false;
        for (RouteCargo c : cargo) {
            MatchedDemand md = findDemandFor(prioritisedDemands, c);
            if (md == null) {
                continue;
            }
            Integer stopOrder = stopOrderByHub.get(c.getDestinationHubId());
            if (stopOrder == null) {
                continue;
            }
            double hours = hoursAtStopOrder.getOrDefault(stopOrder, 0.0);
            double q = QualityCalculator.calculateQualityAtArrival(
                md.lambdaValue, md.avgHarvestDate, hours);
            if (q < md.minQualityThreshold) {
                coldNeeded = true;
                if (reasonOut.length() > 0) {
                    reasonOut.append(" ");
                }
                reasonOut.append(String.format(
                    "%s would arrive at Q=%.2f, below minimum Q=%.2f.",
                    md.produceName, q, md.minQualityThreshold));
            }
        }
        return coldNeeded;
    }

    // Helper: find the matched demand that produced a given cargo item.
    private MatchedDemand findDemandFor(List<MatchedDemand> demands, RouteCargo cargoItem) {
        for (MatchedDemand md : demands) {
            if (md.produceTypeId == cargoItem.getProduceTypeId()
                && md.destinationHubId == cargoItem.getDestinationHubId()) {
                return md;
            }
        }
        return null;
    }

    // Step 6 - save everything to the database.
    // We save the planned route to the database and mark the vehicle as busy.
    private int persistRoute(Vehicle vehicle, List<RouteStop> stops,
                             List<RouteCargo> cargo, boolean requiresColdStorage) {
        int routeId = 0;
        String insertRouteSql =
            "INSERT INTO routes (vehicle_id, status, requires_cold_storage) VALUES (?, ?, ?)";
        String insertStopSql =
            "INSERT INTO route_stops (route_id, hub_id, stop_order) VALUES (?, ?, ?)";
        String insertCargoSql =
            "INSERT INTO route_cargo (route_id, produce_type_id, quantity_kg, "
          + "                         source_hub_id, destination_hub_id) "
          + " VALUES (?, ?, ?, ?, ?)";
        String updateVehicleSql =
            "UPDATE vehicles SET status = ?, current_hub_id = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(insertRouteSql,
                                                              Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, vehicle.getId());
                ps.setString(2, STATUS_PLANNED);
                ps.setBoolean(3, requiresColdStorage);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        routeId = keys.getInt(1);
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(insertStopSql)) {
                for (RouteStop s : stops) {
                    ps.setInt(1, routeId);
                    ps.setInt(2, s.getHubId());
                    ps.setInt(3, s.getStopOrder());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            try (PreparedStatement ps = conn.prepareStatement(insertCargoSql)) {
                for (RouteCargo c : cargo) {
                    ps.setInt(1, routeId);
                    ps.setInt(2, c.getProduceTypeId());
                    ps.setDouble(3, c.getQuantityKg());
                    ps.setInt(4, c.getSourceHubId());
                    ps.setInt(5, c.getDestinationHubId());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            int lastHubId = stops.get(stops.size() - 1).getHubId();
            try (PreparedStatement ps = conn.prepareStatement(updateVehicleSql)) {
                ps.setString(1, STATUS_IN_TRANSIT);
                ps.setInt(2, lastHubId);
                ps.setInt(3, vehicle.getId());
                ps.executeUpdate();
            }

            conn.commit();
        } catch (SQLException e) {
            System.err.println("persistRoute failed: " + e.getMessage());
        }
        return routeId;
    }

    // Step 7 - assemble the friendly RouteResult that the API returns.
    private RouteResult buildResult(int routeId, Vehicle vehicle,
                                     List<RouteStop> stops, List<RouteCargo> cargo,
                                     Map<Integer, Double> qualityAtEachStop,
                                     boolean requiresColdStorage, String coldReason,
                                     int sourceHubId) {

        // Fill the joined-in display fields so the frontend has nice names.
        fillStopHubNames(stops);

        RouteResult result = new RouteResult();
        result.setRouteId(routeId);
        result.setVehicleId(vehicle.getId());
        result.setVehicleName(vehicle.getName());
        result.setStops(stops);
        result.setCargo(cargo);
        result.setQualityAtEachStop(qualityAtEachStop);
        result.setRequiresColdStorage(requiresColdStorage);
        result.setColdStorageReason(coldReason);
        result.setHumanReadableSummary(buildSummarySentence(vehicle, stops, cargo,
                                                            requiresColdStorage, coldReason));
        return result;
    }

    // Helper: builds the long human-friendly sentence that explains the result.
    private String buildSummarySentence(Vehicle vehicle, List<RouteStop> stops,
                                         List<RouteCargo> cargo,
                                         boolean requiresColdStorage, String coldReason) {
        StringBuilder sb = new StringBuilder();
        sb.append("Vehicle ").append(vehicle.getName()).append(" will travel ");

        for (int i = 0; i < stops.size(); i++) {
            RouteStop s = stops.get(i);
            sb.append(s.getHubName() != null ? s.getHubName() : ("Hub " + s.getHubId()));
            if (i < stops.size() - 1) {
                sb.append(" → ");
            }
        }

        sb.append(" delivering ");
        for (int i = 0; i < cargo.size(); i++) {
            RouteCargo c = cargo.get(i);
            sb.append(String.format("%.0fkg ", c.getQuantityKg()));
            sb.append(c.getProduceName() != null ? c.getProduceName()
                                                  : ("produce#" + c.getProduceTypeId()));
            if (i < cargo.size() - 1) {
                sb.append(i == cargo.size() - 2 ? " and " : ", ");
            }
        }
        sb.append(". Cold storage required: ");
        sb.append(requiresColdStorage ? "YES" : "NO");
        if (requiresColdStorage && coldReason != null && !coldReason.isEmpty()) {
            sb.append(" (").append(coldReason).append(")");
        }
        sb.append(".");
        return sb.toString();
    }

    // Helper: looks up the display name for each stop's hub so the JSON output is friendly.
    private void fillStopHubNames(List<RouteStop> stops) {
        if (stops.isEmpty()) {
            return;
        }
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < stops.size(); i++) {
            placeholders.append(i == 0 ? "?" : ",?");
        }
        String sql = "SELECT id, name FROM hubs WHERE id IN (" + placeholders + ")";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < stops.size(); i++) {
                ps.setInt(i + 1, stops.get(i).getHubId());
            }
            Map<Integer, String> nameById = new HashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    nameById.put(rs.getInt("id"), rs.getString("name"));
                }
            }
            for (RouteStop s : stops) {
                if (s.getHubName() == null) {
                    s.setHubName(nameById.get(s.getHubId()));
                }
            }
        } catch (SQLException e) {
            System.err.println("fillStopHubNames query failed: " + e.getMessage());
        }
    }

    // Helper: pick the first IDLE vehicle parked at the source hub, or null if none.
    private Vehicle pickIdleVehicle(int sourceHubId) {
        String sql = "SELECT id, name, capacity_kg, current_hub_id, status "
                   + "  FROM vehicles "
                   + " WHERE current_hub_id = ? AND status = ? "
                   + " ORDER BY id ASC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sourceHubId);
            ps.setString(2, STATUS_IDLE);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Vehicle v = new Vehicle();
                    v.setId(rs.getInt("id"));
                    v.setName(rs.getString("name"));
                    v.setCapacityKg(rs.getDouble("capacity_kg"));
                    v.setCurrentHubId(rs.getInt("current_hub_id"));
                    v.setStatus(rs.getString("status"));
                    return v;
                }
            }
        } catch (SQLException e) {
            System.err.println("pickIdleVehicle query failed: " + e.getMessage());
        }
        return null;
    }

    // Helper: build a RouteResult that reports "nothing happened, here is why".
    private RouteResult buildEmptyResult(String reason) {
        RouteResult result = new RouteResult();
        result.setRouteId(0);
        result.setRequiresColdStorage(false);
        result.setHumanReadableSummary(reason);
        return result;
    }
}
