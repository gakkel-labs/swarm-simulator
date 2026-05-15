package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.util.List;
import java.util.Objects;

public final class BoidsRules {

    private static final double MIN_SEPARATION_DISTANCE = 1e-10;

    private final BoidsConfig config;

    public BoidsRules(BoidsConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public Vector3D separation(Agent agent, List<Agent> neighbors) {
        if (neighbors.isEmpty()) return Vector3D.ZERO;

        Vector3D sum = Vector3D.ZERO;
        for (Agent neighbor : neighbors) {
            Vector3D away = agent.position().subtract(neighbor.position());
            if (away.magnitude() > MIN_SEPARATION_DISTANCE) {
                sum = sum.add(away);
            }
        }
        return sum.normalize();
    }

    public Vector3D alignment(List<Agent> neighbors) {
        if (neighbors.isEmpty()) return Vector3D.ZERO;

        Vector3D sum = Vector3D.ZERO;
        for (Agent neighbor : neighbors) {
            sum = sum.add(neighbor.velocity());
        }
        return sum.normalize();
    }

    public Vector3D cohesion(Agent agent, List<Agent> neighbors) {
        if (neighbors.isEmpty()) return Vector3D.ZERO;

        Vector3D sum = Vector3D.ZERO;
        for (Agent neighbor : neighbors) {
            sum = sum.add(neighbor.position());
        }
        Vector3D centroid = sum.scale(1.0 / neighbors.size());
        return centroid.subtract(agent.position()).normalize();
    }

    public Vector3D steer(Agent agent, List<Agent> neighbors) {
        return separation(agent, neighbors).scale(config.separationWeight())
                .add(alignment(neighbors).scale(config.alignmentWeight()))
                .add(cohesion(agent, neighbors).scale(config.cohesionWeight()));
    }
}
