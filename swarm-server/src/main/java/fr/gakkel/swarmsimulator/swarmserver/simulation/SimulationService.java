package fr.gakkel.swarmsimulator.swarmserver.simulation;

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

    public SimulationService(World world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    public void placeTarget(Vector3D position, TriggerSource source) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(source, "source");
        if (!world.isInBounds(position)) {
            // A drone never leaves the search area, so it could never find this target.
            throw new IllegalArgumentException("target " + position + " is outside the search area");
        }
        world.setTarget(new Target(position));
        LOG.info("Target placed at {} (source={})", position, source);
    }
}
