package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.util.List;
import java.util.Objects;
import java.util.Random;

public final class BoidsRules {

    private static final double MIN_SEPARATION_DISTANCE = 1e-10;
    private static final double OBSTACLE_FALLOFF = 5.0;

    private final BoidsConfig config;
    private final Random rng;

    public BoidsRules(BoidsConfig config) {
        this(config, new Random());
    }

    BoidsRules(BoidsConfig config, Random rng) {
        this.config = Objects.requireNonNull(config, "config");
        this.rng    = Objects.requireNonNull(rng, "rng");
    }

    public Vector3D separation(Agent agent, List<Agent> neighbors) {
        if (neighbors.isEmpty()) return Vector3D.ZERO;

        Vector3D sum = Vector3D.ZERO;
        for (Agent neighbor : neighbors) {
            Vector3D away = agent.position().subtract(neighbor.position());
            double dist = away.magnitude();
            if (dist > MIN_SEPARATION_DISTANCE) {
                sum = sum.add(away.scale(1.0 / (dist * dist)));
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

    public Vector3D boundaryRepulsion(Agent agent, World world) {
        Vector3D pos = agent.position();
        double radius = config.boundaryRepulsionRadius();
        Vector3D force = Vector3D.ZERO;

        force = force.add(wallForce(pos.x(),                  1,  0,  0, radius));
        force = force.add(wallForce(world.width()  - pos.x(), -1, 0,  0, radius));
        force = force.add(wallForce(pos.y(),                  0,  1,  0, radius));
        force = force.add(wallForce(world.height() - pos.y(), 0, -1,  0, radius));
        force = force.add(wallForce(pos.z(),                  0,  0,  1, radius));
        force = force.add(wallForce(world.depth()  - pos.z(), 0,  0, -1, radius));

        // normalize() returns ZERO when forces cancel (e.g. agent equidistant from two opposing
        // walls in a very small world) — no repulsion is the correct silent behaviour in that case
        return force.normalize();
    }

    // (nx, ny, nz) is already a unit vector — passed literally as (±1, 0, 0) etc. by boundaryRepulsion.
    // Scaling by 1/dist (instead of normalizing) is intentional: an inverse-distance force grows as the
    // agent approaches the wall, producing a smooth turn rather than a constant push.
    private static Vector3D wallForce(double dist, double nx, double ny, double nz, double radius) {
        if (dist >= radius || dist < MIN_SEPARATION_DISTANCE) return Vector3D.ZERO;
        double inverseDistanceMagnitude = 1.0 / dist;
        return new Vector3D(nx * inverseDistanceMagnitude, ny * inverseDistanceMagnitude, nz * inverseDistanceMagnitude);
    }

    // Unlike the other rules, obstacleAvoidance does NOT normalize its output: the magnitude
    // grows exponentially as the agent approaches a sphere surface, so navigation curves
    // smoothly around obstacles instead of snapping at the threshold. Walls (boundaryRepulsion)
    // form a closed box and can stay binary; obstacles must be steered around, so they need
    // distance-modulated force. maxSpeed clamping in SimulationLoop keeps things bounded.
    public Vector3D obstacleAvoidance(Agent agent, List<Obstacle> obstacles) {
        if (obstacles.isEmpty()) return Vector3D.ZERO;

        double avoidanceRadius = config.obstacleAvoidanceRadius();
        Vector3D total = Vector3D.ZERO;
        for (Obstacle obstacle : obstacles) {
            Vector3D delta = agent.position().subtract(obstacle.position());
            double distance = delta.magnitude();
            double gap = distance - obstacle.radius();
            // skip degenerate case (agent at center → no direction) and obstacles beyond the threshold
            if (distance < MIN_SEPARATION_DISTANCE || gap >= avoidanceRadius) continue;

            double normalizedGap = Math.max(gap, 0) / avoidanceRadius;
            double strength = Math.exp(-OBSTACLE_FALLOFF * normalizedGap);
            total = total.add(delta.scale(strength / distance));  // delta/distance is the unit direction
        }
        return total;
    }

    public Vector3D wander() {
        double dx = rng.nextDouble(-1, 1);
        double dy = rng.nextDouble(-1, 1);
        double dz = rng.nextDouble(-1, 1);
        // normalize() returns ZERO if all components are 0 (probability ≈ 0) — silent no-op is acceptable
        return new Vector3D(dx, dy, dz).normalize();
    }

    // Inverse-square repulsion (same pattern as obstacleAvoidance: not normalised so force grows
    // sharply as predator closes in, giving a reactive flee rather than a constant drift).
    public Vector3D predatorFlee(Agent agent, Predator predator) {
        Vector3D away = agent.position().subtract(predator.position());
        double dist = away.magnitude();
        if (dist > config.threatFleeRadius() || dist < MIN_SEPARATION_DISTANCE) return Vector3D.ZERO;
        return away.scale(1.0 / (dist * dist));
    }

    // package-private: classical Boids composition (separation + alignment + cohesion).
    // Public callers must use steer(agent, neighbors, world) which adds environmental forces.
    Vector3D steer(Agent agent, List<Agent> neighbors) {
        return separation(agent, neighbors).scale(config.separationWeight())
                .add(alignment(neighbors).scale(config.alignmentWeight()))
                .add(cohesion(agent, neighbors).scale(config.cohesionWeight()));
    }

    public Vector3D steer(Agent agent, List<Agent> neighbors, World world) {
        Vector3D result = steer(agent, neighbors)
                .add(boundaryRepulsion(agent, world).scale(config.boundaryRepulsionWeight()))
                .add(obstacleAvoidance(agent, world.obstacles()).scale(config.obstacleAvoidanceWeight()));
        if (world.predator() != null) {
            result = result.add(predatorFlee(agent, world.predator()).scale(config.threatFleeWeight()));
        }
        result = result.add(wander().scale(config.wanderWeight()));
        return result;
    }
}
