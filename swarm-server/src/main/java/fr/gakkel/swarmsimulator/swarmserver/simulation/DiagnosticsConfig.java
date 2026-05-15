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
        private double flockingLostFraction      = 0.6;
        private double stabilityTolerance        = 0.20;
        private int    stabilitySamples          = 3;

        private Builder() {}

        public Builder immobileThresholdFraction(double v) { immobileThresholdFraction = v; return this; }
        public Builder frozenThresholdFraction(double v)   { frozenThresholdFraction = v;   return this; }
        public Builder dispersalLimitFraction(double v)    { dispersalLimitFraction = v;    return this; }
        public Builder flockingLostFraction(double v)      { flockingLostFraction = v;      return this; }
        public Builder stabilityTolerance(double v)        { stabilityTolerance = v;        return this; }
        public Builder stabilitySamples(int v)             { stabilitySamples = v;          return this; }

        public DiagnosticsConfig build() {
            return new DiagnosticsConfig(immobileThresholdFraction, frozenThresholdFraction,
                    dispersalLimitFraction, flockingLostFraction, stabilityTolerance, stabilitySamples);
        }
    }
}
