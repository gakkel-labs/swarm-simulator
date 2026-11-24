package fr.gakkel.swarmsimulator.swarmserver.server;

import fr.gakkel.swarmsimulator.swarmserver.domain.World;
import io.gakkel.swarm.contracts.v1.SubscribeRequest;
import io.gakkel.swarm.contracts.v1.SwarmObserverGrpc;
import io.gakkel.swarm.contracts.v1.WorldState;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;

public class SwarmObserverImpl extends SwarmObserverGrpc.SwarmObserverImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(SwarmObserverImpl.class);
    static final int STREAM_RATE_HZ = 20;
    public static final String CLIENT_ID = "client_id";

    private final WorldStateBuilder worldStateBuilder;
    private final Set<ServerCallStreamObserver<WorldState>> subscribers = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService broadcaster;

    /** Builds the service without sending anything yet: {@link #start()} begins the stream. */
    public SwarmObserverImpl(World world, double sensorRadiusMeters,
                             DoubleSupplier cohesionSpreadM, ScheduledExecutorService executor) {
        this.worldStateBuilder = new WorldStateBuilder(world, sensorRadiusMeters, cohesionSpreadM);
        this.broadcaster = Objects.requireNonNull(executor, "executor");
    }

    /** Starts sending the world state to every client, {@value #STREAM_RATE_HZ} times per second. */
    public void start() {
        broadcaster.scheduleAtFixedRate(this::broadcast, 0, 1000L / STREAM_RATE_HZ, TimeUnit.MILLISECONDS);
    }

    @Override
    public void subscribeWorldState(SubscribeRequest request, StreamObserver<WorldState> responseObserver) {
        String clientId = request.getClientId();
        MDC.put(CLIENT_ID, clientId);
        try {
            // On the server side gRPC always passes a ServerCallStreamObserver: we need it for
            // isReady() and the cancel handler. Fail the call clearly if this ever changes.
            if (!(responseObserver instanceof ServerCallStreamObserver<WorldState> clientStream)) {
                LOG.error("unexpected observer type {}, refusing the subscription",
                        responseObserver.getClass().getName());
                responseObserver.onError(Status.INTERNAL
                        .withDescription("server-side stream observer expected")
                        .asRuntimeException());
                return;
            }
            subscribers.add(clientStream);
            LOG.info("client subscribed - {} total", subscribers.size());

            clientStream.setOnCancelHandler(() -> {
                MDC.put(CLIENT_ID, clientId);
                try {
                    subscribers.remove(clientStream);
                    LOG.info("client disconnected - {} remaining", subscribers.size());
                } finally {
                    MDC.remove(CLIENT_ID);
                }
            });
        } finally {
            MDC.remove(CLIENT_ID);
        }
    }

    /**
     * Closes the stream of each client, then stops the broadcaster. A StreamObserver is not
     * thread-safe, so the streams are closed on the broadcaster thread, after the last broadcast,
     * never at the same time as an onNext.
     */
    public void stop() {
        broadcaster.execute(this::closeAllClientStreams);
        broadcaster.shutdown();
    }

    private void closeAllClientStreams() {
        for (ServerCallStreamObserver<WorldState> clientStream : subscribers) {
            try {
                clientStream.onCompleted();
            } catch (RuntimeException exception) {
                LOG.warn("could not close a client stream", exception);
            }
        }
        subscribers.clear();
    }

    // scheduleAtFixedRate cancels a task for good, without any log, the first time it throws.
    void broadcast() {
        if (subscribers.isEmpty()) return;
        WorldState state;
        try {
            state = worldStateBuilder.build();
        } catch (RuntimeException exception) {
            LOG.error("Could not build the world state, skipping this frame", exception);
            return;
        }
        for (ServerCallStreamObserver<WorldState> clientStream : subscribers) {
            if (clientStream.isCancelled()) {
                subscribers.remove(clientStream);
                continue;
            }
            // Flow control: a slow client is not ready. Each frame holds the whole world, so
            // skipping one is safe, while sending anyway would pile frames up in server memory.
            if (!clientStream.isReady()) continue;
            try {
                clientStream.onNext(state);
            } catch (RuntimeException exception) {
                LOG.warn("push failed, removing subscriber", exception);
                subscribers.remove(clientStream);
            }
        }
    }
}
