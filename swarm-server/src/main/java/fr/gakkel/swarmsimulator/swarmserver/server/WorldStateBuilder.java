package fr.gakkel.swarmsimulator.swarmserver.server;

import fr.gakkel.swarmsimulator.swarmserver.domain.Agent;
import fr.gakkel.swarmsimulator.swarmserver.domain.Obstacle;
import fr.gakkel.swarmsimulator.swarmserver.domain.Predator;
import fr.gakkel.swarmsimulator.swarmserver.domain.Target;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;
import fr.gakkel.swarmsimulator.swarmserver.simulation.SimulationConstants;

import java.util.function.DoubleSupplier;
import io.gakkel.swarm.contracts.v1.AgentState;
import io.gakkel.swarm.contracts.v1.AgentType;
import io.gakkel.swarm.contracts.v1.PredatorState;
import io.gakkel.swarm.contracts.v1.SearchStatus;
import io.gakkel.swarm.contracts.v1.TargetFoundEvent;
import io.gakkel.swarm.contracts.v1.Vec3;
import io.gakkel.swarm.contracts.v1.WorldState;

import java.util.Objects;

/**
 * Converts the in-memory {@link World} snapshot into the gRPC {@link WorldState} message.
 * Encapsulates NED axis remapping, proto enum mapping, and optional fields (target, predator).
 */
public class WorldStateBuilder {

    private final World world;
    private final DoubleSupplier cohesionSpreadM;

    public WorldStateBuilder(World world, DoubleSupplier cohesionSpreadM) {
        this.world = Objects.requireNonNull(world, "world");
        this.cohesionSpreadM = Objects.requireNonNull(cohesionSpreadM, "cohesionSpreadM");
    }

    public WorldState build() {
        WorldState.Builder worldStateBuilder = WorldState.newBuilder()
                .setTimestampUnixMs(System.currentTimeMillis())
                .setSensorRadiusM((float) SimulationConstants.SENSOR_RADIUS_M)
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
                    .setTargetPosition(toVec3(target.position()))
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

    AgentState toAgentState(Agent agent) {
        return AgentState.newBuilder()
                .setId(agent.id().toString())
                .setType(toProtoType(agent.type()))
                .setPositionXyz(toVec3(agent.position()))
                .setVelocityMps(toVec3(agent.velocity()))
                .build();
    }

    PredatorState toPredatorState(Predator predator) {
        return PredatorState.newBuilder()
                .setId("predator-0")
                .setPositionXyz(toVec3(predator.position()))
                .setVelocityMps(toVec3(predator.velocity()))
                .build();
    }

    private static AgentType toProtoType(fr.gakkel.swarmsimulator.swarmserver.domain.AgentType type) {
        return switch (type) {
            case EXPLORER -> AgentType.AGENT_TYPE_EXPLORER;
            case OPERATOR -> AgentType.AGENT_TYPE_OPERATOR;
            case CARRIER  -> AgentType.AGENT_TYPE_CARRIER;
            // Defensive default: switch is exhaustive at compile time, but this guards against
            // binary-incompatible class evolution if the proto enum is updated independently.
            default       -> throw new IllegalStateException("Unmapped AgentType: " + type);
        };
    }

    private static Vec3 toVec3(Vector3D vector) {
        // Server uses Y=0 at the water surface; negative Y is underwater (AUVs), positive Y is above (drones).
        // Proto contract is NED (North=X, East=Y, Down=Z): Down = -server.Y maps naturally to this convention
        // (surface: server.Y=0 → NED.Down=0; sea floor: server.Y=-depth → NED.Down=depth).
        return Vec3.newBuilder()
                .setX((float)  vector.z())
                .setY((float)  vector.x())
                .setZ((float) -vector.y())
                .build();
    }

    private static io.gakkel.swarm.contracts.v1.Obstacle toProtoObstacle(Obstacle obstacle) {
        return io.gakkel.swarm.contracts.v1.Obstacle.newBuilder()
                .setPositionXyz(toVec3(obstacle.position()))
                .setRadiusM((float) obstacle.radius())
                .build();
    }
}
