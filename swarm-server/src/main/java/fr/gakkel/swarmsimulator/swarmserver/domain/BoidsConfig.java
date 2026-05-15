package fr.gakkel.swarmsimulator.swarmserver.domain;

public record BoidsConfig(
        double perceptionRadius,
        double separationWeight,
        double alignmentWeight,
        double cohesionWeight) {

    public BoidsConfig {
        if (perceptionRadius <= 0) {
            throw new IllegalArgumentException("perceptionRadius must be positive");
        }
        if (separationWeight < 0 || alignmentWeight < 0 || cohesionWeight < 0) {
            throw new IllegalArgumentException("weights must be >= 0");
        }
    }
}
