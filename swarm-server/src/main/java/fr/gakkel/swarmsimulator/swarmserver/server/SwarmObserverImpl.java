package fr.gakkel.swarmsimulator.swarmserver.server;

import fr.gakkel.swarmsimulator.swarmserver.domain.World;
import io.gakkel.swarm.contracts.v1.SubscribeRequest;

import java.util.function.DoubleSupplier;
import io.gakkel.swarm.contracts.v1.SwarmObserverGrpc;
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

    private final WorldStateBuilder worldStateBuilder;
    private final Set<ServerCallStreamObserver<WorldState>> subscribers = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService broadcaster;

    public SwarmObserverImpl(World world, DoubleSupplier cohesionSpreadM, ScheduledExecutorService executor) {
        this.worldStateBuilder = new WorldStateBuilder(world, cohesionSpreadM);
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
        WorldState state = worldStateBuilder.build();
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
}
