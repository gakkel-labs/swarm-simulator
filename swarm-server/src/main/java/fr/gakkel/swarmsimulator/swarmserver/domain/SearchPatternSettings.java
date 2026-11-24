package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.util.Objects;

/**
 * The search pattern chosen in a fleet configuration, with its own settings. It says which
 * pattern and how it is tuned; the simulation builds a new {@link SearchPattern} from it for
 * each run, with a random generator taken from that run's seed.
 *
 * <p>The list is sealed: when a new pattern is added (lawnmower, ant colony...), the compiler
 * shows every {@code switch} that must handle it.
 */
public sealed interface SearchPatternSettings {

    /** Free flocking, tuned by the Boids weights and radii. */
    record Boids(BoidsConfig config) implements SearchPatternSettings {

        public Boids {
            Objects.requireNonNull(config, "config");
        }
    }
}
