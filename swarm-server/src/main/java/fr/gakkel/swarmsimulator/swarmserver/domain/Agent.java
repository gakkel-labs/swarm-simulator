package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.util.Objects;
import java.util.UUID;

public final class Agent {

    private final UUID id;
    private final AgentType type;
    // One volatile value instead of two: see Motion.
    private volatile Motion motion;

    public Agent(UUID id, AgentType type, Vector3D position, Vector3D velocity) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.motion = new Motion(position, velocity);
    }

    public static Agent create(AgentType type, Vector3D position) {
        return new Agent(UUID.randomUUID(), type, position, Vector3D.ZERO);
    }

    public UUID id() {
        return id;
    }

    public AgentType type() {
        return type;
    }

    public Motion motion() {
        return motion;
    }

    public Vector3D position() {
        return motion.position();
    }

    public Vector3D velocity() {
        return motion.velocity();
    }

    // called each simulation step: one write, so readers see both values of the same step
    public void update(Vector3D newPosition, Vector3D newVelocity) {
        this.motion = new Motion(newPosition, newVelocity);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Agent agent)) return false;
        return id.equals(agent.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Agent[id=" + id + ", type=" + type + ", position=" + motion.position()
                + ", velocity=" + motion.velocity() + "]";
    }
}
