package fr.gakkel.swarmsimulator.swarmserver.server;

import fr.gakkel.swarmsimulator.swarmserver.domain.Agent;
import fr.gakkel.swarmsimulator.swarmserver.domain.Obstacle;
import fr.gakkel.swarmsimulator.swarmserver.domain.Predator;
import fr.gakkel.swarmsimulator.swarmserver.domain.Target;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;
import fr.gakkel.swarmsimulator.swarmserver.simulation.SimulationConstants;
import io.gakkel.swarm.contracts.v1.AgentState;
import io.gakkel.swarm.contracts.v1.AgentType;
import io.gakkel.swarm.contracts.v1.PredatorState;
import io.gakkel.swarm.contracts.v1.SearchStatus;
import io.gakkel.swarm.contracts.v1.SubscribeRequest;
import io.gakkel.swarm.contracts.v1.SwarmObserverGrpc;
import io.gakkel.swarm.contracts.v1.TargetFoundEvent;
import io.gakkel.swarm.contracts.v1.Vec3;
import io.gakkel.swarm.contracts.v1.WorldState;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SwarmObserverImpl extends SwarmObserverGrpc.SwarmObserverImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(SwarmObserverImpl.class);
    static final int STREAM_RATE_HZ = 20;
    public static final String CLIENT_ID = "client_id";

    private final World world;
    private final Set<ServerCallStreamObserver<WorldState>> subscribers = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService broadcaster;

    public SwarmObserverImpl(World world, ScheduledExecutorService executor) {
        this.world = world;
        this.broadcaster = executor;
        broadcaster.scheduleAtFixedRate(this::broadcast, 0, 1000L / STREAM_RATE_HZ, TimeUnit.MILLISECONDS);
    }

    @Override
    public void subscribeWorldState(SubscribeRequest request, StreamObserver<WorldState> responseObserver) {
        String clientId = request.getClientId();
        MDC.put(CLIENT_ID, clientId);
        try {
            ServerCallStreamObserver<WorldState> obs = (ServerCallStreamObserver<WorldState>) responseObserver;
            subscribers.add(obs);
            LOG.info("client subscribed — {} total", subscribers.size());

            obs.setOnCancelHandler(() -> {
                MDC.put(CLIENT_ID, clientId);
                try {
                    subscribers.remove(obs);
                    LOG.info("client disconnected — {} remaining", subscribers.size());
                } finally {
                    MDC.remove(CLIENT_ID);
                }
            });
        } finally {
            MDC.remove(CLIENT_ID);
        }
    }

    public void stop() {
        broadcaster.shutdown();
    }

    void broadcast() {
        if (subscribers.isEmpty()) return;
        WorldState state = buildWorldState();
        for (ServerCallStreamObserver<WorldState> obs : subscribers) {
            if (obs.isCancelled()) {
                subscribers.remove(obs);
                continue;
            }
            try {
                obs.onNext(state);
            } catch (Exception e) {
                LOG.warn("push failed, removing subscriber", e);
                subscribers.remove(obs);
            }
        }
    }

    WorldState buildWorldState() {
        WorldState.Builder worldStateBuilder = WorldState.newBuilder()
                .setTimestampUnixMs(System.currentTimeMillis())
                .setSensorRadiusM((float) SimulationConstants.SENSOR_RADIUS_M);
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

    PredatorState toPredatorState(Predator predator) {
        return PredatorState.newBuilder()
                .setId("predator-0")
                .setPositionXyz(toVec3(predator.position()))
                .setVelocityMps(toVec3(predator.velocity()))
                .build();
    }

    AgentState toAgentState(Agent agent) {
        return AgentState.newBuilder()
                .setId(agent.id().toString())
                .setType(toProtoType(agent.type()))
                .setPositionXyz(toVec3(agent.position()))
                .setVelocityMps(toVec3(agent.velocity()))
                .build();
    }

    private AgentType toProtoType(fr.gakkel.swarmsimulator.swarmserver.domain.AgentType type) {
        return switch (type) {
            case EXPLORER -> AgentType.AGENT_TYPE_EXPLORER;
            case OPERATOR -> AgentType.AGENT_TYPE_OPERATOR;
            case CARRIER  -> AgentType.AGENT_TYPE_CARRIER;
            // Defensive default: switch is exhaustive at compile time, but this guards against
            // binary-incompatible class evolution if the proto enum is updated independently.
            default       -> throw new IllegalStateException("Unmapped AgentType: " + type);
        };
    }

    private Vec3 toVec3(Vector3D v) {
        // Server uses Y=0 at the water surface; negative Y is underwater (AUVs), positive Y is above (drones).
        // Proto contract is NED (North=X, East=Y, Down=Z): Down = -server.Y maps naturally to this convention
        // (surface: server.Y=0 → NED.Down=0; sea floor: server.Y=-depth → NED.Down=depth).
        return Vec3.newBuilder()
                .setX((float)  v.z())
                .setY((float)  v.x())
                .setZ((float) -v.y())
                .build();
    }

    private io.gakkel.swarm.contracts.v1.Obstacle toProtoObstacle(Obstacle o) {
        return io.gakkel.swarm.contracts.v1.Obstacle.newBuilder()
                .setPositionXyz(toVec3(o.position()))
                .setRadiusM((float) o.radius())
                .build();
    }
}
