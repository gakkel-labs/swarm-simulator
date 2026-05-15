package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class World {

    private final double width;
    private final double height;
    private final double depth;
    private final List<Agent> agents;
    private final List<Obstacle> obstacles;

    public World(double width, double height, double depth) {
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException("World dimensions must be positive");
        }
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.agents = new ArrayList<>();
        this.obstacles = new ArrayList<>();
    }

    public double width () { return width; }
    public double height() { return height; }
    public double depth() { return depth; }

    public void addAgent(Agent agent) {
        Objects.requireNonNull(agent, "agent");
        if (agents.contains(agent)) throw new IllegalArgumentException("agent already in world");
        agents.add(agent);
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

    // squared-distance filter avoids sqrt in the Boids hot loop
    public List<Agent> neighbors(Agent agent, double radius) {
        if (radius < 0) throw new IllegalArgumentException("radius must be >= 0");
        double radiusSq = radius * radius;
        return agents.stream()
                .filter(a -> !a.equals(agent))
                .filter(a -> squaredDistance(agent.position(), a.position()) <= radiusSq)
                .toList();
    }

    public boolean isInBounds(Vector3D position) {
        return position.x() >= 0 && position.x() <= width
                && position.y() >= 0 && position.y() <= height
                && position.z() >= 0 && position.z() <= depth;
    }

    public Vector3D clamp(Vector3D position) {
        return new Vector3D(
                Math.clamp(position.x(), 0, width),
                Math.clamp(position.y(), 0, height),
                Math.clamp(position.z(), 0, depth));
    }

    private static double squaredDistance(Vector3D a, Vector3D b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
