package fr.gakkel.swarmsimulator.swarmserver.server;

import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;
import fr.gakkel.swarmsimulator.swarmserver.simulation.DiagnosticsConfig;
import fr.gakkel.swarmsimulator.swarmserver.simulation.SimulationLoop;
import fr.gakkel.swarmsimulator.swarmserver.simulation.SimulationService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionServiceV1;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Wires together the gRPC server, the simulation loop, the world-state broadcaster and the
 * simulation control service. Keeps {@link SwarmServer#main} a single linear sequence of calls
 * and exposes the same wiring to integration tests.
 */
public final class SwarmServerBootstrap {

    private final SimulationLoop simulation;
    private final SwarmObserverImpl observer;
    private final Server grpcServer;

    private SwarmServerBootstrap(SimulationLoop simulation, SwarmObserverImpl observer, Server grpcServer) {
        this.simulation = simulation;
        this.observer = observer;
        this.grpcServer = grpcServer;
    }

    public static SwarmServerBootstrap create(int port) {
        World world = SimulationLoop.createDefaultWorld();
        BoidsConfig boidsConfig = BoidsConfig.builder().build();
        DiagnosticsConfig diagnosticsConfig = DiagnosticsConfig.builder().build();

        ScheduledExecutorService simExecutor = singleDaemonThreadExecutor("sim-loop");
        SimulationLoop simulation = new SimulationLoop(world, boidsConfig, diagnosticsConfig, simExecutor);

        ScheduledExecutorService broadcastExecutor = singleDaemonThreadExecutor("swarm-broadcaster");
        SwarmObserverImpl observer = new SwarmObserverImpl(world, broadcastExecutor);

        Server grpcServer = ServerBuilder.forPort(port)
                .addService(new PingServiceImpl())
                .addService(observer)
                .addService(new SimulationControlImpl(new SimulationService(world)))
                .addService(ProtoReflectionServiceV1.newInstance())
                .build();

        return new SwarmServerBootstrap(simulation, observer, grpcServer);
    }

    public void start() throws IOException {
        grpcServer.start();
        simulation.start();
    }

    public void stop() throws InterruptedException {
        observer.stop();
        grpcServer.shutdown();
        simulation.stop();
    }

    public void awaitTermination() throws InterruptedException {
        grpcServer.awaitTermination();
    }

    private static ScheduledExecutorService singleDaemonThreadExecutor(String threadName) {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, threadName);
            t.setDaemon(true);
            return t;
        });
    }
}
