package com.fasal.algorithm;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// All freshness math lives here. We model produce quality with an exponential
// decay curve: Q(t) = e^(-lambda * t), where t is days since harvest and
// lambda is how fast the produce spoils. Q goes from 1.0 (just picked) to 0.0.
public class QualityCalculator {

    // Number of hours in a day - used to convert travel time in hours to days.
    private static final double HOURS_PER_DAY = 24.0;

    // Returned when we ask "days until spoilage" for produce that never spoils
    // (lambda = 0 means the math has no solution, so we report "forever").
    private static final double INFINITE_DAYS = Double.POSITIVE_INFINITY;

    // The freshest a piece of produce can be, used at the source hub.
    public static final double MAX_QUALITY = 1.0;

    // The lowest a quality value can dip to in our model.
    public static final double MIN_QUALITY = 0.0;

    // Private constructor - this is a utility class with only static methods.
    private QualityCalculator() { }

    // Returns the freshness right now, based on how many days have passed
    // since the produce was harvested.
    // Q(t) = e^(-lambda * t)
    public static double calculateQuality(double lambda, LocalDate harvestDate) {
        double daysSinceHarvest = daysBetween(harvestDate, LocalDate.now());
        return Math.exp(-lambda * daysSinceHarvest);
    }

    // Returns the freshness we expect when the produce arrives at its destination.
    // It adds the truck's travel time (in hours, converted to days) to the days
    // already elapsed since harvest, then applies the same exponential decay.
    public static double calculateQualityAtArrival(double lambda, LocalDate harvestDate,
                                                    double travelTimeHours) {
        double daysSinceHarvest = daysBetween(harvestDate, LocalDate.now());
        double travelDays = travelTimeHours / HOURS_PER_DAY;
        double totalDaysElapsed = daysSinceHarvest + travelDays;
        return Math.exp(-lambda * totalDaysElapsed);
    }

    // Returns how many more days until quality drops below the given threshold.
    // We solve the decay formula for t:
    //   threshold = e^(-lambda * t)
    //   -> ln(threshold) = -lambda * t
    //   -> t = -ln(threshold) / lambda
    // If lambda is zero the produce never spoils, so we return infinity.
    public static double daysUntilThreshold(double lambda, LocalDate harvestDate, double threshold) {
        if (lambda == 0.0) {
            return INFINITE_DAYS;
        }
        if (threshold <= MIN_QUALITY) {
            return INFINITE_DAYS;
        }
        double daysSinceHarvest = daysBetween(harvestDate, LocalDate.now());
        double totalAllowedDays = -Math.log(threshold) / lambda;
        return totalAllowedDays - daysSinceHarvest;
    }

    // Returns true when the produce will arrive below the buyer's minimum
    // acceptable freshness, meaning a refrigerated truck is needed to slow decay.
    public static boolean needsColdStorage(double lambda, LocalDate harvestDate,
                                            double travelTimeHours, double minQualityThreshold) {
        double projectedQuality = calculateQualityAtArrival(lambda, harvestDate, travelTimeHours);
        return projectedQuality < minQualityThreshold;
    }

    // Counts the whole days between two dates. Never returns a negative number,
    // because "the harvest happens tomorrow" should be treated as "happens now"
    // for the purposes of decay.
    private static double daysBetween(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to);
        return Math.max(0L, days);
    }
}
