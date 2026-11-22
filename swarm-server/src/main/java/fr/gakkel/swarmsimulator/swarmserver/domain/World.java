package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class World {

    private final double width;
    private final double height;
    private final double depth;
    // Copy-on-write lists: the simulation thread adds and removes drones (rare), while the
    // broadcast thread reads them 20 times per second. Each iteration works on a stable snapshot,
    // so a read never throws ConcurrentModificationException and never sees a half-done change.
    private final CopyOnWriteArrayList<Agent> agents;
    private final CopyOnWriteArrayList<Obstacle> obstacles;
    private volatile Predator predator;
    private volatile Target target;

    public World(double width, double height, double depth) {
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException("World dimensions must be positive");
        }
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.agents = new CopyOnWriteArrayList<>();
        this.obstacles = new CopyOnWriteArrayList<>();
    }

    public double width () { return width; }
    public double height() { return height; }
    public double depth() { return depth; }

    public void addAgent(Agent agent) {
        Objects.requireNonNull(agent, "agent");
        // addIfAbsent checks and adds in one atomic step (no check-then-act race between threads)
        if (!agents.addIfAbsent(agent)) throw new IllegalArgumentException("agent already in world");
    }

    public boolean removeAgent(Agent agent) {
        return agents.remove(agent);
    }

    public List<Agent> agents() {
        return Collections.unmodifiableList(agents);
    }

    public int agentCount() {
        return agents.size();
    }

    public void addObstacle(Obstacle obstacle) {
        Objects.requireNonNull(obstacle, "obstacle");
        obstacles.add(obstacle);
    }

    public List<Obstacle> obstacles() {
        return Collections.unmodifiableList(obstacles);
    }

    public Predator predator() { return predator; }
    public void setPredator(Predator predator) { this.predator = predator; }

    public Target target() { return target; }
    public void setTarget(Target newTarget) { this.target = newTarget; }

    // squared-distance filter avoids sqrt in the Boids hot loop
    public List<Agent> neighbors(Agent agent, double radius) {
        if (radius < 0) throw new IllegalArgumentException("radius must be >= 0");
        double radiusSq = radius * radius;
        return agents.stream()
                .filter(other -> !other.equals(agent))
                .filter(other -> agent.position().distanceSquaredTo(other.position()) <= radiusSq)
                .toList();
    }

    public boolean isInBounds(Vector3D position) {
        return position.x() >= 0 && position.x() <= width
                && position.y() >= -height && position.y() <= 0
                && position.z() >= 0 && position.z() <= depth;
    }

    public Vector3D clamp(Vector3D position) {
        return new Vector3D(
                Math.clamp(position.x(), 0, width),
                Math.clamp(position.y(), -height, 0),
                Math.clamp(position.z(), 0, depth));
    }
}
