package fr.gakkel.swarmsimulator.swarmserver.simulation;

import fr.gakkel.swarmsimulator.swarmserver.domain.Agent;
import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.Obstacle;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Computes and logs flocking diagnostics (centroid, drift, σ, alerts, flocking state, obstacle avoidance).
 * Stateful: tracks centroid history and σ trend across calls to detect divergence and flocking stability.
 *
 * <p>Not thread-safe — must be called from the simulation loop thread.
 * Only {@link #flockingConfirmed()} may be polled from another thread (volatile field).
 */
public class FlockingDiagnostician {

    private static final Logger LOG = LoggerFactory.getLogger(FlockingDiagnostician.class);

    private final BoidsConfig boidsConfig;
    private final DiagnosticsConfig diagnosticsConfig;

    private Vector3D lastCentroid = null;
    private double previousPositionStandardDeviation = 0;
    private int consecutiveStandardDeviationIncreases = 0;
    private final List<Double> recentSigmas = new ArrayList<>();
    private volatile boolean flockingConfirmed = false;

    public FlockingDiagnostician(BoidsConfig boidsConfig, DiagnosticsConfig diagnosticsConfig) {
        this.boidsConfig = Objects.requireNonNull(boidsConfig, "boidsConfig");
        this.diagnosticsConfig = Objects.requireNonNull(diagnosticsConfig, "diagnosticsConfig");
    }

    public boolean flockingConfirmed() {
        return flockingConfirmed;
    }

    /**
     * Compute centroid + σ, log them, then evaluate alerts and flocking state.
     * Called periodically by the simulation loop (every LOG_INTERVAL_TICKS ticks).
     *
     * @param elapsedSeconds  simulated time elapsed since start (seconds)
     * @param intervalSeconds time between two diagnostic samples (seconds)
     * @param world           world snapshot to inspect
     */
    public void record(int elapsedSeconds, double intervalSeconds, World world) {
        List<Agent> agents = world.agents();
        if (agents.isEmpty()) return;

        Vector3D centroid = SimulationLoop.computeCentroid(agents);
        double positionStandardDeviation = SimulationLoop.computePositionStandardDeviation(agents, centroid);
        double standardDeviationRounded = Math.round(positionStandardDeviation * 10) / 10.0;

        if (lastCentroid == null) {
            LOG.info("t={}s | premier releve | σ={} u", elapsedSeconds, standardDeviationRounded);
        } else {
            double drift = centroid.distanceTo(lastCentroid) / intervalSeconds;
            double driftRounded = Math.round(drift * 100) / 100.0;
            LOG.info("t={}s | drift={} u/s | σ={} u", elapsedSeconds, driftRounded, standardDeviationRounded);
            checkAlerts(elapsedSeconds, world, drift, driftRounded, positionStandardDeviation, standardDeviationRounded);
            checkFlocking(elapsedSeconds, world, positionStandardDeviation, standardDeviationRounded);
        }

        if (!world.obstacles().isEmpty()) {
            logObstacleAvoidance(elapsedSeconds, agents, world.obstacles());
        }

        lastCentroid = centroid;
        previousPositionStandardDeviation = positionStandardDeviation;
    }

    private void checkAlerts(int elapsedSeconds, World world, double drift, double driftRounded,
                             double positionStandardDeviation, double standardDeviationRounded) {
        if (drift < boidsConfig.maxSpeed() * diagnosticsConfig.immobileThresholdFraction()) {
            LOG.warn("t={}s | ALERTE essaim immobile (drift={} u/s)", elapsedSeconds, driftRounded);
        }

        if (!flockingConfirmed) {
            double standardDeviationLimit = Math.min(world.width(), Math.min(world.height(), world.depth()))
                    * diagnosticsConfig.dispersalLimitFraction();
            if (positionStandardDeviation > standardDeviationLimit) {
                LOG.warn("t={}s | ALERTE essaim disperse (σ={} u > {} u)",
                        elapsedSeconds, standardDeviationRounded, (int) standardDeviationLimit);
            }
        }

        if (positionStandardDeviation > previousPositionStandardDeviation) {
            consecutiveStandardDeviationIncreases++;
            if (consecutiveStandardDeviationIncreases >= diagnosticsConfig.stabilitySamples()) {
                LOG.warn("t={}s | ALERTE divergence ({} relevés en hausse)",
                        elapsedSeconds, consecutiveStandardDeviationIncreases);
            }
        } else {
            consecutiveStandardDeviationIncreases = 0;
        }

        long frozenCount = world.agents().stream()
                .filter(agent -> agent.velocity().magnitude() < boidsConfig.maxSpeed() * diagnosticsConfig.frozenThresholdFraction())
                .count();
        if (frozenCount > 0) {
            LOG.warn("t={}s | ALERTE {} agent(s) figé(s)", elapsedSeconds, frozenCount);
        }
    }

    private void checkFlocking(int elapsedSeconds, World world,
                               double positionStandardDeviation, double standardDeviationRounded) {
        if (flockingConfirmed) {
            double criticalLimit = Math.min(world.width(), Math.min(world.height(), world.depth()))
                    * diagnosticsConfig.flockingLostFraction();
            if (positionStandardDeviation > criticalLimit) {
                flockingConfirmed = false;
                recentSigmas.clear();
                LOG.warn("t={}s | flocking lost — σ={} u depasse seuil critique ({} u)",
                        elapsedSeconds, standardDeviationRounded, (int) criticalLimit);
            }
            return;
        }
        recentSigmas.add(positionStandardDeviation);
        int samples = diagnosticsConfig.stabilitySamples();
        if (recentSigmas.size() > samples) recentSigmas.removeFirst();
        if (recentSigmas.size() == samples) {
            double max = recentSigmas.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            double min = recentSigmas.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            if (max > 0 && (max - min) / max < diagnosticsConfig.stabilityTolerance()) {
                flockingConfirmed = true;
                LOG.info("t={}s | flocking confirmed — σ stable à {} u", elapsedSeconds, standardDeviationRounded);
            }
        }
    }

    private void logObstacleAvoidance(int elapsedSeconds, List<Agent> agents, List<Obstacle> obstacles) {
        long penetrations = SimulationLoop.countAgentsInsideObstacles(agents, obstacles);
        double gap = SimulationLoop.minObstacleGap(agents, obstacles);
        double gapRounded = Math.round(gap * 10) / 10.0;
        if (penetrations > 0) {
            LOG.warn("t={}s | ALERTE {} agent(s) dans obstacle (gap min={} u)",
                    elapsedSeconds, penetrations, gapRounded);
        } else {
            LOG.info("t={}s | min obstacle gap={} u", elapsedSeconds, gapRounded);
        }
    }
}
