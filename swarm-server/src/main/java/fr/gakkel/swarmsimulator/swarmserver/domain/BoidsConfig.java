package fr.gakkel.swarmsimulator.swarmserver.domain;

public record BoidsConfig(
        double perceptionRadius,
        double separationWeight,
        double alignmentWeight,
        double cohesionWeight,
        double maxSpeed,
        double boundaryRepulsionRadius,
        double boundaryRepulsionWeight) {

    public BoidsConfig {
        if (perceptionRadius <= 0) {
            throw new IllegalArgumentException("perceptionRadius must be positive");
        }
        if (separationWeight < 0 || alignmentWeight < 0 || cohesionWeight < 0) {
            throw new IllegalArgumentException("weights must be >= 0");
        }
        if (maxSpeed <= 0) {
            throw new IllegalArgumentException("maxSpeed must be positive");
        }
        if (boundaryRepulsionRadius <= 0) {
            throw new IllegalArgumentException("boundaryRepulsionRadius must be positive");
        }
        if (boundaryRepulsionWeight < 0) {
            throw new IllegalArgumentException("boundaryRepulsionWeight must be >= 0");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private double perceptionRadius      = 15.0;
        private double separationWeight      = 1.5;
        private double alignmentWeight       = 1.0;
        private double cohesionWeight        = 1.0;
        private double maxSpeed              = 5.0;
        private double boundaryRepulsionRadius = 15.0;
        private double boundaryRepulsionWeight = 2.0;

        private Builder() {}

        public Builder perceptionRadius(double v)       { perceptionRadius = v;       return this; }
        public Builder separationWeight(double v)       { separationWeight = v;       return this; }
        public Builder alignmentWeight(double v)        { alignmentWeight = v;        return this; }
        public Builder cohesionWeight(double v)         { cohesionWeight = v;         return this; }
        public Builder maxSpeed(double v)               { maxSpeed = v;               return this; }
        public Builder boundaryRepulsionRadius(double v){ boundaryRepulsionRadius = v; return this; }
        public Builder boundaryRepulsionWeight(double v){ boundaryRepulsionWeight = v; return this; }

        public BoidsConfig build() {
            return new BoidsConfig(perceptionRadius, separationWeight, alignmentWeight,
                    cohesionWeight, maxSpeed, boundaryRepulsionRadius, boundaryRepulsionWeight);
        }
    }
}
