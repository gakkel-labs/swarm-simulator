package fr.gakkel.swarmsimulator.swarmserver.server;

import fr.gakkel.swarmsimulator.swarmserver.domain.Agent;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;
import io.gakkel.swarm.contracts.v1.AgentState;
import io.gakkel.swarm.contracts.v1.AgentType;
import io.gakkel.swarm.contracts.v1.SubscribeRequest;
import io.gakkel.swarm.contracts.v1.SwarmObserverGrpc;
import io.gakkel.swarm.contracts.v1.Vec3;
import io.gakkel.swarm.contracts.v1.WorldState;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SwarmObserverImpl extends SwarmObserverGrpc.SwarmObserverImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(SwarmObserverImpl.class);
    static final int STREAM_RATE_HZ = 20;

    private final World world;
    private final Set<ServerCallStreamObserver<WorldState>> subscribers = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService broadcaster;

    public SwarmObserverImpl(World world) {
        this(world, Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "swarm-broadcaster");
            t.setDaemon(true);
            return t;
        }));
    }

    // package-private: allows tests to inject a mock executor and drive broadcast() manually
    SwarmObserverImpl(World world, ScheduledExecutorService executor) {
        this.world = world;
        this.broadcaster = executor;
        broadcaster.scheduleAtFixedRate(this::broadcast, 0, 1000L / STREAM_RATE_HZ, TimeUnit.MILLISECONDS);
    }

    @Override
    public void subscribeWorldState(SubscribeRequest request, StreamObserver<WorldState> responseObserver) {
        ServerCallStreamObserver<WorldState> obs = (ServerCallStreamObserver<WorldState>) responseObserver;
        subscribers.add(obs);
        LOG.info("client '{}' subscribed — {} total", request.getClientId(), subscribers.size());

        obs.setOnCancelHandler(() -> {
            subscribers.remove(obs);
            LOG.info("client '{}' disconnected — {} remaining", request.getClientId(), subscribers.size());
        });
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
        WorldState.Builder builder = WorldState.newBuilder()
                .setTimestampUnixMs(System.currentTimeMillis());
        for (Agent agent : world.agents()) {
            builder.addAgents(toAgentState(agent));
        }
        return builder.build();
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
        };
    }

    private Vec3 toVec3(Vector3D v) {
        return Vec3.newBuilder()
                .setX((float) v.x())
                .setY((float) v.y())
                .setZ((float) v.z())
                .build();
    }
}
