package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.util.Objects;
import java.util.Random;

/** Free flocking: each drone follows the Boids rules with the neighbors it can perceive. */
public final class BoidsSearchPattern implements SearchPattern {

    private final BoidsRules rules;
    private final double perceptionRadius;

    /** {@code wanderRandom} drives the wander force: give it a generator built from the seed. */
    public BoidsSearchPattern(BoidsConfig config, Random wanderRandom) {
        Objects.requireNonNull(config, "config");
        this.rules = new BoidsRules(config, Objects.requireNonNull(wanderRandom, "wanderRandom"));
        this.perceptionRadius = config.perceptionRadius();
    }

    @Override
    public Vector3D steer(Agent drone, World world) {
        return rules.steer(drone, world.neighbors(drone, perceptionRadius), world);
    }
}
