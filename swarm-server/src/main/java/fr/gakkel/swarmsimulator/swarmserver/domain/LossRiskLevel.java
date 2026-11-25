package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * How likely the operator thinks it is to lose a drone in the search area (loss risk, see the
 * glossary). Each level gives the chance of losing one drone in 24 h of diving. The values are
 * orders of magnitude taken from published studies of underwater drone losses (ADR-0012).
 *
 * <p>The risk grows with the time spent diving, at a constant rate: a drone that survived the
 * first hour has the same risk in the second hour.
 */
public enum LossRiskLevel {
    NONE(0.0),
    LOW(0.002),
    MEDIUM(0.02),
    HIGH(0.05),
    EXTREME(0.15);

    private static final double SECONDS_PER_DAY = 24 * 3600;

    private final double lossProbabilityPerDay;
    // The loss rate, per second, that gives lossProbabilityPerDay after one day.
    private final double lossRatePerSecond;

    LossRiskLevel(double lossProbabilityPerDay) {
        this.lossProbabilityPerDay = lossProbabilityPerDay;
        // log1p(-p) = ln(1 - p), precise even when p is tiny.
        this.lossRatePerSecond = -Math.log1p(-lossProbabilityPerDay) / SECONDS_PER_DAY;
    }

    /** The chance of losing one drone during 24 h of diving, between 0 and 1. */
    public double lossProbabilityPerDay() {
        return lossProbabilityPerDay;
    }

    /**
     * The chance of losing one drone during {@code seconds} of diving: 1 - e^(-rate x seconds).
     * Over one simulation step this is tiny, so it is computed with {@code expm1} to keep it
     * precise.
     */
    public double lossProbabilityDuring(double seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException("duration must not be negative, got " + seconds);
        }
        return -Math.expm1(-lossRatePerSecond * seconds);
    }

    /** Reads a level from a setting value such as {@code medium}; case and spaces are ignored. */
    public static LossRiskLevel fromSetting(String value) {
        String name = value.trim().toUpperCase(Locale.ROOT);
        for (LossRiskLevel level : values()) {
            if (level.name().equals(name)) {
                return level;
            }
        }
        String allowedValues = Arrays.stream(values())
                .map(level -> level.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
        throw new IllegalArgumentException("loss risk level '" + value
                + "' is unknown; expected one of: " + allowedValues);
    }
}
