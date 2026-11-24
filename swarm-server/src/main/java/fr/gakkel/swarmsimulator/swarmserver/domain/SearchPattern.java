package fr.gakkel.swarmsimulator.swarmserver.domain;

/**
 * The way the drones move to cover the search area (Boids, lawnmower, ant colony...). It is a
 * setting of the fleet configuration, so several patterns can be compared on the same mission.
 *
 * <p>A search pattern only says where a drone wants to go. The speed limit, the obstacles and
 * the walls are applied after it by the simulation, the same way for every pattern.
 */
public interface SearchPattern {

    /**
     * The steering force for {@code drone}, computed from the world at the start of the step:
     * the simulation asks every drone before any of them moves.
     */
    Vector3D steer(Agent drone, World world);
}
