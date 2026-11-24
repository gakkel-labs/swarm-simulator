package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoidsSearchPatternTest {

    // Wander on, so the test also checks that the random force comes from the given generator.
    private static final BoidsConfig CONFIG = BoidsConfig.builder().wanderWeight(0.5).build();

    @Test
    void steer_givesTheSameForceAsTheBoidsRulesWithTheNeighborsInPerceptionRadius() {
        World world = new World(100, 100, 50);
        Agent drone = Agent.create(AgentType.EXPLORER, new Vector3D(50, -50, 25));
        world.addAgent(drone);
        world.addAgent(Agent.create(AgentType.EXPLORER, new Vector3D(55, -50, 25)));  // neighbor
        world.addAgent(Agent.create(AgentType.EXPLORER, new Vector3D(90, -50, 25)));  // too far
        SearchPattern boids = new BoidsSearchPattern(CONFIG, new Random(7L));
        BoidsRules rules = new BoidsRules(CONFIG, new Random(7L));
        Vector3D expected = rules.steer(drone, world.neighbors(drone, CONFIG.perceptionRadius()), world);

        Vector3D steer = boids.steer(drone, world);

        assertEquals(expected, steer);
    }
}
