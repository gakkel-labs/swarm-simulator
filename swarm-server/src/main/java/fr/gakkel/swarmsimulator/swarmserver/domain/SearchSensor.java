package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.util.Objects;

import static fr.gakkel.swarmsimulator.swarmserver.domain.SpecificationChecks.requireNotNegative;
import static fr.gakkel.swarmsimulator.swarmserver.domain.SpecificationChecks.requirePositive;

/**
 * A sensor that sees a strip of the seabed and creates contacts (ADR-0011, section 4).
 *
 * <p>The list is sealed: Step 2 only has the side-scan sonar. When a new search sensor comes
 * (synthetic aperture sonar, magnetometer, camera...), the compiler shows every {@code switch}
 * that must handle it.
 */
public sealed interface SearchSensor {

    /** How far from its track the sensor can detect something, in meters. */
    double maxRangeMeters();

    /**
     * A side-scan sonar: a strip of seabed on each side of the drone, with a gap right under it.
     *
     * @param rangePerSideMeters              the range of one side
     * @param nadirHalfWidthMeters            half the width of the gap under the drone (the
     *                                        nadir); 0 means no gap
     * @param detectionCurve                  the chance of detection across the swath
     * @param falseContactsPerSquareKilometer false contacts per km² of seabed swept
     */
    record SideScanSonar(
            double rangePerSideMeters,
            double nadirHalfWidthMeters,
            DetectionCurve detectionCurve,
            double falseContactsPerSquareKilometer) implements SearchSensor {

        public SideScanSonar {
            requirePositive("rangePerSideMeters", rangePerSideMeters);
            requireNotNegative("nadirHalfWidthMeters", nadirHalfWidthMeters);
            if (nadirHalfWidthMeters >= rangePerSideMeters) {
                throw new IllegalArgumentException("nadirHalfWidthMeters (" + nadirHalfWidthMeters
                        + ") must be smaller than rangePerSideMeters (" + rangePerSideMeters + ")");
            }
            Objects.requireNonNull(detectionCurve, "detectionCurve");
            double fullUntil = detectionCurve.fullProbabilityUntilMeters();
            if (fullUntil <= nadirHalfWidthMeters || fullUntil > rangePerSideMeters) {
                throw new IllegalArgumentException("fullProbabilityUntilMeters (" + fullUntil
                        + ") must be between nadirHalfWidthMeters (" + nadirHalfWidthMeters
                        + ") and rangePerSideMeters (" + rangePerSideMeters + ")");
            }
            requireNotNegative("falseContactsPerSquareKilometer", falseContactsPerSquareKilometer);
        }

        @Override
        public double maxRangeMeters() {
            return rangePerSideMeters;
        }
    }
}
