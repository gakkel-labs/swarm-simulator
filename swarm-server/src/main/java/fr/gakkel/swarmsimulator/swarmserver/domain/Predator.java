package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Predator {

    private final UUID id;
    private final PredatorConfig config;
    // One volatile value instead of two: see Motion.
    private volatile Motion motion;

    public Predator(Vector3D position, Vector3D velocity) {
        this(UUID.randomUUID(), position, velocity, PredatorConfig.defaults());
    }

    public Predator(UUID id, Vector3D position, Vector3D velocity, PredatorConfig config) {
        this.id = Objects.requireNonNull(id, "id");
        this.config = Objects.requireNonNull(config, "config");
        this.motion = new Motion(position, velocity);
    }

    public UUID id() { return id; }
    public Motion motion() { return motion; }
    public Vector3D position() { return motion.position(); }
    public Vector3D velocity() { return motion.velocity(); }

    public void update(World world, double dt) {
        List<Agent> agents = world.agents();
        if (agents.isEmpty()) return;

        Vector3D currentPosition = motion.position();
        Agent nearest = findNearest(agents, currentPosition);
        Vector3D direction = nearest.position().subtract(currentPosition).normalize();
        Vector3D newVelocity = direction.scale(config.speed());
        Vector3D newPosition = world.clamp(currentPosition.add(newVelocity.scale(dt)));
        motion = new Motion(newPosition, newVelocity);
    }

    public Agent tryEat(World world) {
        Vector3D currentPosition = motion.position();
        for (Agent agent : world.agents()) {
            if (currentPosition.distanceTo(agent.position()) < config.eatRadius()) {
                world.removeAgent(agent);
                return agent;
            }
        }
        return null;
    }

    private static Agent findNearest(List<Agent> agents, Vector3D fromPosition) {
        Agent nearest = null;
        double minDistanceSquared = Double.MAX_VALUE;
        for (Agent agent : agents) {
            double distanceSquared = fromPosition.distanceSquaredTo(agent.position());
            if (distanceSquared < minDistanceSquared) {
                minDistanceSquared = distanceSquared;
                nearest = agent;
            }
        }
        return nearest;
    }
}
