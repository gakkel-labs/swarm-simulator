package fr.gakkel.swarmsimulator.swarmserver.server;

import fr.gakkel.swarmsimulator.swarmserver.domain.Agent;
import fr.gakkel.swarmsimulator.swarmserver.domain.Obstacle;
import fr.gakkel.swarmsimulator.swarmserver.domain.Predator;
import fr.gakkel.swarmsimulator.swarmserver.domain.Target;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;
import fr.gakkel.swarmsimulator.swarmserver.simulation.SimulationConstants;

import java.util.function.DoubleSupplier;
import io.gakkel.swarm.contracts.v1.AgentState;
import io.gakkel.swarm.contracts.v1.AgentType;
import io.gakkel.swarm.contracts.v1.PredatorState;
import io.gakkel.swarm.contracts.v1.SearchStatus;
import io.gakkel.swarm.contracts.v1.TargetFoundEvent;
import io.gakkel.swarm.contracts.v1.WorldState;

import java.util.Objects;

/**
 * Converts the in-memory {@link World} snapshot into the gRPC {@link WorldState} message.
 * Maps proto enums and optional fields (target, predator). Axis remapping is in
 * {@link CoordinateConversions}.
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

    AgentState toAgentState(Agent agent) {
        return AgentState.newBuilder()
                .setId(agent.id().toString())
                .setType(toProtoType(agent.type()))
                .setPositionXyz(CoordinateConversions.serverToNed(agent.position()))
                .setVelocityMps(CoordinateConversions.serverToNed(agent.velocity()))
                .build();
    }

    PredatorState toPredatorState(Predator predator) {
        return PredatorState.newBuilder()
                .setId("predator-0")
                .setPositionXyz(CoordinateConversions.serverToNed(predator.position()))
                .setVelocityMps(CoordinateConversions.serverToNed(predator.velocity()))
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
