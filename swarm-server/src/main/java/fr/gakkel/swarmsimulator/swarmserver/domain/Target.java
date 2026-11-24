package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class Target {

    record FoundRecord(String agentId, double foundAtElapsedS) {}

    private final Vector3D position;
    private final SimulationClock clock;
    private final double placedAtSeconds;
    private final AtomicReference<FoundRecord> foundRecord = new AtomicReference<>();

    /** Times are read from {@code clock}, the simulation time, never from the computer's clock. */
    public Target(Vector3D position, SimulationClock clock) {
        this.position = Objects.requireNonNull(position, "position");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.placedAtSeconds = clock.elapsedSeconds();
    }

    public void markFound(String agentId) {
        foundRecord.compareAndSet(null, new FoundRecord(agentId, elapsedSeconds()));
    }

    public double elapsedSeconds() {
        return clock.elapsedSeconds() - placedAtSeconds;
    }

    public Vector3D position()      { return position; }
    public boolean isFound()        { return foundRecord.get() != null; }
    public String foundByAgentId()  { FoundRecord record = foundRecord.get(); return record != null ? record.agentId() : null; }
    public double foundAtElapsedS() { FoundRecord record = foundRecord.get(); return record != null ? record.foundAtElapsedS() : 0.0; }
}
