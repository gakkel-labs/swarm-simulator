package fr.gakkel.swarmsimulator.swarmserver.server;

import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;
import fr.gakkel.swarmsimulator.swarmserver.simulation.DiagnosticsConfig;
import fr.gakkel.swarmsimulator.swarmserver.simulation.SimulationLoop;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionServiceV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class SwarmServer {

    private static final Logger LOG = LoggerFactory.getLogger(SwarmServer.class);
    private static final int PORT = 50051;

    public static void main(String[] args) throws IOException, InterruptedException {
        World world = SimulationLoop.createDefaultWorld();
        BoidsConfig boids = BoidsConfig.builder().build();
        DiagnosticsConfig diag = DiagnosticsConfig.builder().build();
        SimulationLoop sim = new SimulationLoop(world, boids, diag);

        SwarmObserverImpl observer = new SwarmObserverImpl(world);

        Server server = ServerBuilder.forPort(PORT)
                .addService(new PingServiceImpl())
                .addService(observer)
                .addService(ProtoReflectionServiceV1.newInstance())
                .build()
                .start();

        sim.start();
        LOG.info("SwarmServer on :{} — sim {}Hz — stream {}Hz",
                PORT, SimulationLoop.TICK_RATE_HZ, SwarmObserverImpl.STREAM_RATE_HZ);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down...");
            server.shutdown();
            try {
                sim.stop();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        server.awaitTermination();
    }
}
