package fr.gakkel.swarmsimulator.swarmserver.simulation;

import fr.gakkel.swarmsimulator.swarmserver.domain.EndConditions;
import fr.gakkel.swarmsimulator.swarmserver.domain.FleetConfiguration;
import fr.gakkel.swarmsimulator.swarmserver.domain.Mission;
import fr.gakkel.swarmsimulator.swarmserver.domain.Scenario;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;

import java.util.Objects;
import java.util.Optional;

/**
 * The scoring of one fleet configuration on one mission: many simulations, each with its own
 * seed, all with the {@link SimulationProfile#SCORING} profile. The score itself (mission success
 * probability) is computed from their results in #82.
 *
 * <p>Simulation number {@code i} always gets the same seed, so the same master seed gives the
 * same results, whatever the order or the threads they run on. This class has no thread: the
 * caller decides how many simulations run side by side.
 */
public final class Scoring {

    /** The profile of every simulation of a scoring: the same step for every configuration. */
    public static final SimulationProfile PROFILE = SimulationProfile.SCORING;

    // SplitMix64 constants: they spread consecutive indexes over the whole range of seeds, so
    // two simulations of a scoring never start from close seeds.
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
    private static final long MIX_MULTIPLIER_1 = 0xBF58476D1CE4E5B9L;
    private static final long MIX_MULTIPLIER_2 = 0x94D049BB133111EBL;

    private final FleetConfiguration fleet;
    private final Mission mission;
    private final Optional<Vector3D> wreckPosition;
    private final long masterSeed;

    /**
     * @param wreckPosition where the wreck lies in every simulation, or empty for none. Where
     *                      the wreck lies in each scenario comes with the score (#82)
     * @throws IllegalArgumentException if the wreck is outside the search area of the mission
     */
    public Scoring(FleetConfiguration fleet, Mission mission, Optional<Vector3D> wreckPosition,
                   long masterSeed) {
        this.fleet = Objects.requireNonNull(fleet, "fleet");
        this.mission = Objects.requireNonNull(mission, "mission");
        this.wreckPosition = Objects.requireNonNull(wreckPosition, "wreckPosition");
        this.masterSeed = masterSeed;
        World searchVolume = new World(mission.searchArea(), mission.seabedDepthMeters());
        wreckPosition.ifPresent(position -> {
            if (!searchVolume.isInBounds(position)) {
                throw new IllegalArgumentException("wreck " + position
                        + " is outside the search area " + mission.searchArea());
            }
        });
    }

    /** The limit time of each simulation: the endurance of the drones that come back first. */
    public double limitTimeSeconds() {
        return fleet.shortestEndurance().toMillis() / 1000.0;
    }

    /** The seed of simulation number {@code simulationIndex}, drawn from the master seed. */
    public long seedOf(int simulationIndex) {
        if (simulationIndex < 0) {
            throw new IllegalArgumentException(
                    "simulation index must be >= 0, got " + simulationIndex);
        }
        long mixed = masterSeed + GOLDEN_GAMMA * (simulationIndex + 1L);
        mixed = (mixed ^ (mixed >>> 30)) * MIX_MULTIPLIER_1;
        mixed = (mixed ^ (mixed >>> 27)) * MIX_MULTIPLIER_2;
        return mixed ^ (mixed >>> 31);
    }

    /**
     * Runs simulation number {@code simulationIndex} to its end and returns its result. Each call
     * builds its own simulation: calls can run on several threads at the same time.
     */
    public SimulationResult simulate(int simulationIndex) {
        long seed = seedOf(simulationIndex);
        Scenario scenario = wreckPosition
                .map(position -> Scenario.withWreckAt(position, seed))
                .orElseGet(() -> Scenario.withoutWreck(seed));
        Simulation simulation = Simulation.create(fleet, mission, scenario,
                EndConditions.missionEnd(limitTimeSeconds()), DemoOptions.none());
        return simulation.runToEnd(PROFILE.stepSeconds());
    }
}
