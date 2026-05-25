package fr.gakkel.swarmsimulator.swarmserver.domain;

public record BoidsConfig(
        double perceptionRadius,
        double separationWeight,
        double alignmentWeight,
        double cohesionWeight,
        double wanderWeight,
        double maxSpeed,
        double boundaryRepulsionRadius,
        double boundaryRepulsionWeight,
        double obstacleAvoidanceRadius,
        double obstacleAvoidanceWeight,
        double threatFleeRadius,
        double threatFleeWeight) {

    public BoidsConfig {
        if (perceptionRadius <= 0) {
            throw new IllegalArgumentException("perceptionRadius must be positive");
        }
        if (separationWeight < 0 || alignmentWeight < 0 || cohesionWeight < 0 || wanderWeight < 0) {
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
        if (obstacleAvoidanceRadius <= 0) {
            throw new IllegalArgumentException("obstacleAvoidanceRadius must be positive");
        }
        if (obstacleAvoidanceWeight < 0) {
            throw new IllegalArgumentException("obstacleAvoidanceWeight must be >= 0");
        }
        if (threatFleeRadius <= 0) {
            throw new IllegalArgumentException("threatFleeRadius must be positive");
        }
        if (threatFleeWeight < 0) {
            throw new IllegalArgumentException("threatFleeWeight must be >= 0");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private double perceptionRadius        = 25.0;
        private double separationWeight        = 1.8;
        private double alignmentWeight         = 1.5;
        private double cohesionWeight          = 1.5;
        private double wanderWeight            = 0.0;
        private double maxSpeed                = 5.0;
        private double boundaryRepulsionRadius = 15.0;
        private double boundaryRepulsionWeight = 2.0;
        private double obstacleAvoidanceRadius = 8.0;
        private double obstacleAvoidanceWeight = 4.0;
        private double threatFleeRadius        = 15.0;
        private double threatFleeWeight        = 200.0;

        private Builder() {}

        public Builder perceptionRadius(double value)        { perceptionRadius = value;        return this; }
        public Builder separationWeight(double value)        { separationWeight = value;        return this; }
        public Builder alignmentWeight(double value)         { alignmentWeight = value;         return this; }
        public Builder cohesionWeight(double value)          { cohesionWeight = value;          return this; }
        public Builder wanderWeight(double value)            { wanderWeight = value;            return this; }
        public Builder maxSpeed(double value)                { maxSpeed = value;                return this; }
        public Builder boundaryRepulsionRadius(double value) { boundaryRepulsionRadius = value; return this; }
        public Builder boundaryRepulsionWeight(double value) { boundaryRepulsionWeight = value; return this; }
        public Builder obstacleAvoidanceRadius(double value) { obstacleAvoidanceRadius = value; return this; }
        public Builder obstacleAvoidanceWeight(double value) { obstacleAvoidanceWeight = value; return this; }
        public Builder threatFleeRadius(double value)        { threatFleeRadius = value;        return this; }
        public Builder threatFleeWeight(double value)        { threatFleeWeight = value;        return this; }

        public BoidsConfig build() {
            return new BoidsConfig(perceptionRadius, separationWeight, alignmentWeight,
                    cohesionWeight, wanderWeight, maxSpeed,
                    boundaryRepulsionRadius, boundaryRepulsionWeight,
                    obstacleAvoidanceRadius, obstacleAvoidanceWeight,
                    threatFleeRadius, threatFleeWeight);
        }
    }
}
