package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.util.Objects;
import java.util.UUID;

public final class Agent {

    private final UUID id;
    private final AgentType type;
    private Vector3D position;
    private Vector3D velocity;

    public Agent(UUID id, AgentType type, Vector3D position, Vector3D velocity) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.position = Objects.requireNonNull(position, "position");
        this.velocity = Objects.requireNonNull(velocity, "velocity");
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

    public Vector3D position() {
        return position;
    }

    public Vector3D velocity() {
        return velocity;
    }

    // called each simulation step — mutates in-place to avoid allocation at 20 Hz
    public void update(Vector3D newPosition, Vector3D newVelocity) {
        this.position = Objects.requireNonNull(newPosition, "newPosition");
        this.velocity = Objects.requireNonNull(newVelocity, "newVelocity");
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
        return "Agent[id=" + id + ", type=" + type + ", position=" + position + ", velocity=" + velocity + "]";
    }
}
