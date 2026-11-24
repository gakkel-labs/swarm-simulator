package fr.gakkel.swarmsimulator.swarmserver.simulation;

import fr.gakkel.swarmsimulator.swarmserver.domain.SimulationClock;
import fr.gakkel.swarmsimulator.swarmserver.domain.Target;
import fr.gakkel.swarmsimulator.swarmserver.domain.TriggerSource;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class SimulationService {

    private static final Logger LOG = LoggerFactory.getLogger(SimulationService.class);

    private final World world;
    private final SimulationClock clock;

    public SimulationService(World world, SimulationClock clock) {
        this.world = Objects.requireNonNull(world, "world");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void placeTarget(Vector3D position, TriggerSource source) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(source, "source");
        if (!world.isInBounds(position)) {
            // A drone never leaves the search area, so it could never find this target.
            throw new IllegalArgumentException("target " + position + " is outside the search area");
        }
        world.setTarget(new Target(position, clock));
        LOG.info("Target placed at {} (source={})", position, source);
    }
}
