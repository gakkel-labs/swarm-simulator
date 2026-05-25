package fr.gakkel.swarmsimulator.swarmserver.simulation;

public record DiagnosticsConfig(
        double immobileThresholdFraction,
        double frozenThresholdFraction,
        double dispersalLimitFraction,
        double flockingLostFraction,
        double stabilityTolerance,
        int stabilitySamples) {

    public DiagnosticsConfig {
        if (immobileThresholdFraction <= 0) throw new IllegalArgumentException("immobileThresholdFraction must be positive");
        if (frozenThresholdFraction <= 0)   throw new IllegalArgumentException("frozenThresholdFraction must be positive");
        if (dispersalLimitFraction <= 0)    throw new IllegalArgumentException("dispersalLimitFraction must be positive");
        if (flockingLostFraction <= 0)      throw new IllegalArgumentException("flockingLostFraction must be positive");
        if (stabilityTolerance <= 0)        throw new IllegalArgumentException("stabilityTolerance must be positive");
        if (stabilitySamples < 2)           throw new IllegalArgumentException("stabilitySamples must be >= 2");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private double immobileThresholdFraction = 0.02;
        private double frozenThresholdFraction   = 0.002;
        private double dispersalLimitFraction    = 0.8;
        private double flockingLostFraction      = 0.9;
        private double stabilityTolerance        = 0.20;
        private int    stabilitySamples          = 3;

        private Builder() {}

        public Builder immobileThresholdFraction(double value) { immobileThresholdFraction = value; return this; }
        public Builder frozenThresholdFraction(double value)   { frozenThresholdFraction = value;   return this; }
        public Builder dispersalLimitFraction(double value)    { dispersalLimitFraction = value;    return this; }
        public Builder flockingLostFraction(double value)      { flockingLostFraction = value;      return this; }
        public Builder stabilityTolerance(double value)        { stabilityTolerance = value;        return this; }
        public Builder stabilitySamples(int value)             { stabilitySamples = value;          return this; }

        public DiagnosticsConfig build() {
            return new DiagnosticsConfig(immobileThresholdFraction, frozenThresholdFraction,
                    dispersalLimitFraction, flockingLostFraction, stabilityTolerance, stabilitySamples);
        }
    }
}
