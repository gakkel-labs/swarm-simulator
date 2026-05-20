package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.util.Objects;

public final class Target {

    private final Vector3D position;
    private final long placedAtNanos;

    private volatile boolean found;
    private volatile String foundByAgentId;
    private volatile double foundAtElapsedS;

    public Target(Vector3D position) {
        this.position = Objects.requireNonNull(position, "position");
        this.placedAtNanos = System.nanoTime();
    }

    public void markFound(String agentId) {
        if (found) return;
        this.foundAtElapsedS = elapsedSeconds();
        this.foundByAgentId = agentId;
        this.found = true;
    }

    public double elapsedSeconds() {
        return (System.nanoTime() - placedAtNanos) / 1_000_000_000.0;
    }

    public Vector3D position()      { return position; }
    public boolean isFound()        { return found; }
    public String foundByAgentId()  { return foundByAgentId; }
    public double foundAtElapsedS() { return foundAtElapsedS; }
}
