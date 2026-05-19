package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.util.List;
import java.util.Objects;

public final class Predator {

    static final double SPEED      = 3.0;
    static final double EAT_RADIUS = 1.5;

    private volatile Vector3D position;
    private volatile Vector3D velocity;

    public Predator(Vector3D position, Vector3D velocity) {
        this.position = Objects.requireNonNull(position, "position");
        this.velocity = Objects.requireNonNull(velocity, "velocity");
    }

    public Vector3D position() { return position; }
    public Vector3D velocity() { return velocity; }

    public void update(World world, double dt) {
        List<Agent> agents = world.agents();
        if (agents.isEmpty()) return;

        Agent nearest = findNearest(agents);
        Vector3D direction = nearest.position().subtract(position).normalize();
        velocity = direction.scale(SPEED);
        position = world.clamp(position.add(velocity.scale(dt)));
    }

    public Agent tryEat(World world) {
        for (Agent agent : world.agents()) {
            if (position.distanceTo(agent.position()) < EAT_RADIUS) {
                world.removeAgent(agent);
                return agent;
            }
        }
        return null;
    }

    private Agent findNearest(List<Agent> agents) {
        Agent nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Agent agent : agents) {
            double dist = position.distanceTo(agent.position());
            if (dist < minDist) {
                minDist = dist;
                nearest = agent;
            }
        }
        return nearest;
    }
}
