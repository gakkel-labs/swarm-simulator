package fr.gakkel.swarmsimulator.swarmserver.domain;

/**
 * When a run stops. The live demo never stops: the operator places a new target and the swarm
 * goes on. A batch run stops like a mission: wreck detected, limit time reached, or no drone
 * left. The coverage threshold will be one more reason once coverage is computed (#133).
 *
 * <p>The list is sealed: a new kind of end shows every {@code switch} that must handle it.
 */
public sealed interface EndConditions {

    /** The run of the live demo: it never ends. */
    record Never() implements EndConditions {}

    /**
     * The run of a mission scenario.
     *
     * @param limitTimeSeconds the limit time of the mission, in seconds of simulation time
     */
    record MissionEnd(double limitTimeSeconds) implements EndConditions {

        public MissionEnd {
            // "!(x > 0)" also rejects NaN, which fails every comparison.
            if (!(limitTimeSeconds > 0) || Double.isInfinite(limitTimeSeconds)) {
                throw new IllegalArgumentException(
                        "limit time must be positive and finite, got " + limitTimeSeconds);
            }
        }
    }

    static EndConditions never() {
        return new Never();
    }

    static EndConditions missionEnd(double limitTimeSeconds) {
        return new MissionEnd(limitTimeSeconds);
    }
}
