package fr.gakkel.swarmsimulator.swarmserver.server;

import fr.gakkel.swarmsimulator.swarmserver.domain.Agent;
import fr.gakkel.swarmsimulator.swarmserver.domain.Motion;
import fr.gakkel.swarmsimulator.swarmserver.domain.Obstacle;
import fr.gakkel.swarmsimulator.swarmserver.domain.Predator;
import fr.gakkel.swarmsimulator.swarmserver.domain.Target;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;

import io.gakkel.swarm.contracts.v1.AgentState;
import io.gakkel.swarm.contracts.v1.AgentType;
import io.gakkel.swarm.contracts.v1.PredatorState;
import io.gakkel.swarm.contracts.v1.SearchStatus;
import io.gakkel.swarm.contracts.v1.TargetFoundEvent;
import io.gakkel.swarm.contracts.v1.Vec3;
import io.gakkel.swarm.contracts.v1.WorldState;

import java.time.Clock;
import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * Converts the in-memory {@link World} snapshot into the gRPC {@link WorldState} message.
 * Maps proto enums and optional fields (target, predator). Axis remapping is in
 * {@link CoordinateConversions}.
 */
public class WorldStateBuilder {

    private final World world;
    private final double sensorRadiusMeters;
    private final DoubleSupplier cohesionSpreadM;
    private final Clock clock;

    /**
     * {@code sensorRadiusMeters} is the range shown around each drone by Unity: the longest
     * search sensor range of the fleet (one value for the whole world in the contract).
     */
    public WorldStateBuilder(World world, double sensorRadiusMeters,
                             DoubleSupplier cohesionSpreadM) {
        this(world, sensorRadiusMeters, cohesionSpreadM, Clock.systemUTC());
    }

    /** The clock is injectable so a test can check the timestamp of a frame. */
    public WorldStateBuilder(World world, double sensorRadiusMeters,
                             DoubleSupplier cohesionSpreadM, Clock clock) {
        this.world = Objects.requireNonNull(world, "world");
        this.sensorRadiusMeters = sensorRadiusMeters;
        this.cohesionSpreadM = Objects.requireNonNull(cohesionSpreadM, "cohesionSpreadM");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public WorldState build() {
        WorldState.Builder worldStateBuilder = WorldState.newBuilder()
                .setTimestampUnixMs(clock.millis())
                .setSearchAreaSizeM(searchAreaSizeInNed())
                .setSensorRadiusM((float) sensorRadiusMeters)
                .setCohesionSpreadM((float) cohesionSpreadM.getAsDouble());
        for (Agent agent : world.agents()) {
            worldStateBuilder.addAgents(toAgentState(agent));
        }
        for (Obstacle obstacle : world.obstacles()) {
            worldStateBuilder.addObstacles(toProtoObstacle(obstacle));
        }
        Predator predator = world.predator();
        if (predator != null) {
            worldStateBuilder.addPredators(toPredatorState(predator));
        }
        Target target = world.target();
        if (target != null) {
            SearchStatus.Builder statusBuilder = SearchStatus.newBuilder()
                    .setTargetPlaced(true)
                    .setTargetPosition(CoordinateConversions.serverToNed(target.position()))
                    .setElapsedSimS((float) target.elapsedSeconds());
            if (target.isFound()) {
                statusBuilder.setFoundEvent(TargetFoundEvent.newBuilder()
                        .setAgentId(target.foundByAgentId())
                        .setElapsedSimS((float) target.foundAtElapsedS())
                        .build());
            }
            worldStateBuilder.setSearchStatus(statusBuilder.build());
        }
        return worldStateBuilder.build();
    }

    // The search area goes from the surface (server Y = 0) down to Y = -height. Its far corner,
    // converted like any point, gives the size along North, East and Down (all positive).
    private Vec3 searchAreaSizeInNed() {
        Vector3D farCorner = new Vector3D(world.width(), -world.height(), world.depth());
        return CoordinateConversions.serverToNed(farCorner);
    }

    AgentState toAgentState(Agent agent) {
        Motion motion = agent.motion(); // one read: position and velocity of the same step
        return AgentState.newBuilder()
                .setId(agent.id().toString())
                .setType(toProtoType(agent.type()))
                .setPositionXyz(CoordinateConversions.serverToNed(motion.position()))
                .setVelocityMps(CoordinateConversions.serverToNed(motion.velocity()))
                .build();
    }

    PredatorState toPredatorState(Predator predator) {
        Motion motion = predator.motion(); // one read: position and velocity of the same step
        return PredatorState.newBuilder()
                .setId(predator.id().toString())
                .setPositionXyz(CoordinateConversions.serverToNed(motion.position()))
                .setVelocityMps(CoordinateConversions.serverToNed(motion.velocity()))
                .build();
    }

    private static AgentType toProtoType(fr.gakkel.swarmsimulator.swarmserver.domain.AgentType type) {
        return switch (type) {
            // No default: the compiler checks that every AgentType is mapped.
            case EXPLORER -> AgentType.AGENT_TYPE_EXPLORER;
            case SPECIALIST -> AgentType.AGENT_TYPE_SPECIALIST;
            case CARRIER -> AgentType.AGENT_TYPE_CARRIER;
        };
    }

    private static io.gakkel.swarm.contracts.v1.Obstacle toProtoObstacle(Obstacle obstacle) {
        return io.gakkel.swarm.contracts.v1.Obstacle.newBuilder()
                .setPositionXyz(CoordinateConversions.serverToNed(obstacle.position()))
                .setRadiusM((float) obstacle.radius())
                .build();
    }
}
