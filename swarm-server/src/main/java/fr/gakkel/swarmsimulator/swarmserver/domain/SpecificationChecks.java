package fr.gakkel.swarmsimulator.swarmserver.domain;

/**
 * Checks shared by the parts of a drone specification. Each message names the value, so a
 * mistake in the drone catalog points to the field to fix.
 */
final class SpecificationChecks {

    private SpecificationChecks() {}

    /** Above 0 and finite. "!(value > 0)" also refuses NaN, which fails every comparison. */
    static double requirePositive(String name, double value) {
        if (!(value > 0) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be positive and finite, got " + value);
        }
        return value;
    }

    /** 0 or more, and finite. */
    static double requireNotNegative(String name, double value) {
        if (!(value >= 0) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be >= 0 and finite, got " + value);
        }
        return value;
    }
}
