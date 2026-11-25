package fr.gakkel.swarmsimulator.swarmserver.simulation;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * A first summary of a scoring, for the log: how often the wreck was detected, how long it took
 * on average, and how many drones were lost. The real score (#82) adds the margin of error and a
 * wreck that moves from one scenario to the next.
 *
 * @param simulationCount             how many simulations ran
 * @param detectedCount               how many of them detected the wreck
 * @param meanDetectionTimeSeconds    the mean time to detect the wreck, over the simulations
 *                                    that detected it, or empty if none did
 * @param meanLostDronesPerSimulation the mean number of drones lost in one simulation
 */
public record ScoringSummary(int simulationCount, int detectedCount,
                             OptionalDouble meanDetectionTimeSeconds,
                             double meanLostDronesPerSimulation) {

    private static final double SECONDS_PER_HOUR = 3600.0;

    public ScoringSummary {
        if (simulationCount < 1) {
            throw new IllegalArgumentException(
                    "a scoring summary needs at least one simulation, got " + simulationCount);
        }
        if (detectedCount < 0 || detectedCount > simulationCount) {
            throw new IllegalArgumentException("detected count must be between 0 and "
                    + simulationCount + ", got " + detectedCount);
        }
        Objects.requireNonNull(meanDetectionTimeSeconds, "meanDetectionTimeSeconds");
        if (!(meanLostDronesPerSimulation >= 0)) {
            throw new IllegalArgumentException("mean lost drones must be >= 0, got "
                    + meanLostDronesPerSimulation);
        }
    }

    public static ScoringSummary of(List<SimulationResult> results) {
        if (results.isEmpty()) {
            throw new IllegalArgumentException("a scoring summary needs at least one simulation");
        }
        OptionalDouble meanDetectionTime = results.stream()
                .map(SimulationResult::wreckDetectedAtSeconds)
                .filter(OptionalDouble::isPresent)
                .mapToDouble(OptionalDouble::getAsDouble)
                .average();
        int detectedCount = (int) results.stream()
                .filter(result -> result.wreckDetectedAtSeconds().isPresent())
                .count();
        double meanLostDrones = results.stream()
                .mapToLong(SimulationResult::lostDroneCount)
                .average()
                .orElseThrow();
        return new ScoringSummary(results.size(), detectedCount, meanDetectionTime,
                meanLostDrones);
    }

    /** The share of simulations that detected the wreck, from 0 to 1. */
    public double detectionRate() {
        return (double) detectedCount / simulationCount;
    }

    /** One line for the log, for example "wreck detected 1/4 (25.0 %) | not detected 3 ...". */
    public String describe() {
        int notDetected = simulationCount - detectedCount;
        String meanTime = meanDetectionTimeSeconds.isPresent()
                ? String.format(Locale.ROOT, "%.1f h",
                        meanDetectionTimeSeconds.getAsDouble() / SECONDS_PER_HOUR)
                : "-";
        return String.format(Locale.ROOT, "wreck detected %d/%d (%.1f %%) | not detected %d "
                        + "(%.1f %%) | mean detection time %s | drones lost %.2f per simulation",
                detectedCount, simulationCount, 100 * detectionRate(), notDetected,
                100.0 * notDetected / simulationCount, meanTime, meanLostDronesPerSimulation);
    }
}
