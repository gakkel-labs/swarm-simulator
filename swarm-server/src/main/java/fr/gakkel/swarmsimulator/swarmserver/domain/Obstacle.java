package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.util.Objects;

public record Obstacle(Vector3D position, double radius) {

    public Obstacle {
        Objects.requireNonNull(position, "position");
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
    }
}
