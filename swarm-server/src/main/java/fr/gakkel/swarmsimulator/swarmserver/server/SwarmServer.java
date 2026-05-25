package fr.gakkel.swarmsimulator.swarmserver.server;

import fr.gakkel.swarmsimulator.swarmserver.simulation.SimulationLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwarmServer {

    private static final Logger LOG = LoggerFactory.getLogger(SwarmServer.class);
    private static final int PORT = 50051;

    public static void main(String[] args) throws Exception {
        SwarmServerBootstrap bootstrap = SwarmServerBootstrap.create(PORT);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down...");
            try {
                bootstrap.stop();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        bootstrap.start();
        LOG.info("SwarmServer on :{} — sim {}Hz — stream {}Hz",
                PORT, SimulationLoop.TICK_RATE_HZ, SwarmObserverImpl.STREAM_RATE_HZ);
        bootstrap.awaitTermination();
    }
}
