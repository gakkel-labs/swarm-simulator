package fr.gakkel.swarmsimulator.swarmserver.domain;

import static fr.gakkel.swarmsimulator.swarmserver.domain.SpecificationChecks.requirePositive;

/**
 * How a drone hears its neighbors under water (ADR-0011, section 5).
 *
 * @param rangeMeters          how far a position message carries
 * @param messagePeriodSeconds the time between two position messages of one drone
 * @param messageLossRate      the share of messages lost, from 0 (none) to below 1
 */
public record AcousticModem(double rangeMeters, double messagePeriodSeconds,
                            double messageLossRate) {

    public AcousticModem {
        requirePositive("rangeMeters", rangeMeters);
        requirePositive("messagePeriodSeconds", messagePeriodSeconds);
        // 1 is refused: with every message lost the drones never hear each other.
        if (!(messageLossRate >= 0 && messageLossRate < 1)) {
            throw new IllegalArgumentException(
                    "messageLossRate must be in [0, 1), got " + messageLossRate);
        }
    }
}
