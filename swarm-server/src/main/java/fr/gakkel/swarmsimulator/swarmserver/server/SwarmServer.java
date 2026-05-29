package fr.gakkel.swarmsimulator.swarmserver.server;

import fr.gakkel.swarmsimulator.swarmserver.config.Settings;
import fr.gakkel.swarmsimulator.swarmserver.simulation.SimulationLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwarmServer {

    private static final Logger LOG = LoggerFactory.getLogger(SwarmServer.class);

    public static void main(String[] args) throws Exception {
        Settings settings = Settings.load();
        SwarmServerBootstrap bootstrap = SwarmServerBootstrap.create(settings);

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
                settings.port(), SimulationLoop.TICK_RATE_HZ, SwarmObserverImpl.STREAM_RATE_HZ);
        bootstrap.awaitTermination();
    }
}
