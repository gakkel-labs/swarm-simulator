package fr.gakkel.swarmsimulator.swarmserver.simulation;

import fr.gakkel.swarmsimulator.swarmserver.domain.Agent;
import fr.gakkel.swarmsimulator.swarmserver.domain.AgentType;
import fr.gakkel.swarmsimulator.swarmserver.domain.Obstacle;
import fr.gakkel.swarmsimulator.swarmserver.domain.Predator;
import fr.gakkel.swarmsimulator.swarmserver.domain.PredatorConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;

import java.util.Random;
import java.util.UUID;

/**
 * Builds the starting world of a simulation: drones, obstacles and danger. Every random value
 * (ids, positions, speeds) comes from the generator it is given, so the same seed always gives
 * the same world.
 */
public final class WorldFactory {

    private static final double INITIAL_SPEED_XY = 2.0;
    private static final double INITIAL_SPEED_Z  = 1.0;

    private WorldFactory() {}

    public static World createDefaultWorld() {
        return createWorld(SimulationConstants.DEFAULT_AGENT_COUNT,
                SimulationConstants.DEFAULT_WORLD_WIDTH, SimulationConstants.DEFAULT_WORLD_HEIGHT,
                SimulationConstants.DEFAULT_WORLD_DEPTH, new Random(SimulationConstants.DEFAULT_SEED));
    }

    static World createWorld(int agentCount, Random rng) {
        return createWorld(agentCount,
                SimulationConstants.DEFAULT_WORLD_WIDTH, SimulationConstants.DEFAULT_WORLD_HEIGHT,
                SimulationConstants.DEFAULT_WORLD_DEPTH, rng);
    }

    // Obstacle/predator positions are expressed as fractions of the world dimensions, and obstacle
    // radii scale by the smallest dimension ratio so a sphere never outgrows the smallest extent.
    // At the default 100x100x50 the scale is 1.0, reproducing the original literal positions
    // (50,-50,25)/(25,-70,25)/(75,-30,25), radii (8/5/6) and predator (5,-5,5) exactly.
    static World createWorld(int agentCount, double width, double height, double depth, Random rng) {
        return createWorld(agentCount, width, height, depth, PredatorConfig.defaults(), rng);
    }

    static World createWorld(int agentCount, double width, double height, double depth,
                             PredatorConfig predatorConfig, Random rng) {
        World world = new World(width, height, depth);
        for (int i = 0; i < agentCount; i++) {
            Vector3D pos = new Vector3D(
                    rng.nextDouble(0, width),
                    rng.nextDouble(-height, 0),
                    rng.nextDouble(0, depth));
            Vector3D vel = new Vector3D(
                    rng.nextDouble(-INITIAL_SPEED_XY, INITIAL_SPEED_XY),
                    rng.nextDouble(-INITIAL_SPEED_XY, INITIAL_SPEED_XY),
                    rng.nextDouble(-INITIAL_SPEED_Z, INITIAL_SPEED_Z));
            world.addAgent(new Agent(seededId(rng), AgentType.EXPLORER, pos, vel));
        }
        double radiusScale = Math.min(width / SimulationConstants.DEFAULT_WORLD_WIDTH,
                Math.min(height / SimulationConstants.DEFAULT_WORLD_HEIGHT, depth / SimulationConstants.DEFAULT_WORLD_DEPTH));
        world.addObstacle(new Obstacle(new Vector3D(0.50 * width, -0.50 * height, 0.50 * depth), 8.0 * radiusScale));
        world.addObstacle(new Obstacle(new Vector3D(0.25 * width, -0.70 * height, 0.50 * depth), 5.0 * radiusScale));
        world.addObstacle(new Obstacle(new Vector3D(0.75 * width, -0.30 * height, 0.50 * depth), 6.0 * radiusScale));
        Vector3D predatorStart = new Vector3D(0.05 * width, -0.05 * height, 0.10 * depth);
        world.setPredator(
                new Predator(seededId(rng), predatorStart, Vector3D.ZERO, predatorConfig));
        return world;
    }

    /**
     * A random version-4 UUID drawn from {@code rng} instead of {@code UUID.randomUUID()}, which
     * has no seed: the same seed then gives the same drone ids, so "sorted by id" is the same order.
     */
    static UUID seededId(Random rng) {
        long mostSignificantBits = (rng.nextLong() & ~0xF000L) | 0x4000L;                // version 4
        long leastSignificantBits = (rng.nextLong() & ~(0x3L << 62)) | (0x2L << 62);      // IETF variant
        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
