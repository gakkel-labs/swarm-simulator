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
}
