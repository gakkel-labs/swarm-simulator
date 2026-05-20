package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class Target {

    record FoundRecord(String agentId, double foundAtElapsedS) {}

    private final Vector3D position;
    private final long placedAtNanos;
    private final AtomicReference<FoundRecord> foundRecord = new AtomicReference<>();

    public Target(Vector3D position) {
        this.position = Objects.requireNonNull(position, "position");
        this.placedAtNanos = System.nanoTime();
    }

    public void markFound(String agentId) {
        foundRecord.compareAndSet(null, new FoundRecord(agentId, elapsedSeconds()));
    }

    public double elapsedSeconds() {
        return (System.nanoTime() - placedAtNanos) / 1_000_000_000.0;
    }

    public Vector3D position()      { return position; }
    public boolean isFound()        { return foundRecord.get() != null; }
    public String foundByAgentId()  { FoundRecord record = foundRecord.get(); return record != null ? record.agentId() : null; }
    public double foundAtElapsedS() { FoundRecord record = foundRecord.get(); return record != null ? record.foundAtElapsedS() : 0.0; }
}
