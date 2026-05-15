package fr.gakkel.swarmsimulator.swarmserver.simulation;

import fr.gakkel.swarmsimulator.swarmserver.domain.Agent;
import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SimulationLoopTest {

    private static final BoidsConfig CONFIG = BoidsConfig.builder().build();

    private World world;
    private SimulationLoop loop;

    @BeforeEach
    void setUp() {
        world = SimulationLoop.createWorld(10, new Random(0L));
        loop = new SimulationLoop(world, CONFIG);
    }

    @Test
    void createDefaultWorld_creates20Agents() {
        assertEquals(20, SimulationLoop.createDefaultWorld().agentCount());
    }

    @Test
    void createDefaultWorld_agentsStartInBounds() {
        World w = SimulationLoop.createDefaultWorld();
        for (Agent a : w.agents()) {
            assertTrue(w.isInBounds(a.position()), "agent out of bounds: " + a.position());
        }
    }

    @Test
    void tick_agentsRemainInBoundsAfterManyTicks() {
        for (int i = 0; i < 300; i++) loop.tick();
        for (Agent a : world.agents()) {
            assertTrue(world.isInBounds(a.position()), "agent out of bounds after 300 ticks: " + a.position());
        }
    }

    @Test
    void tick_velocityNeverExceedsMaxSpeed() {
        for (int i = 0; i < 300; i++) loop.tick();
        for (Agent a : world.agents()) {
            assertTrue(a.velocity().magnitude() <= CONFIG.maxSpeed() + 1e-9,
                    "velocity exceeds maxSpeed: " + a.velocity().magnitude());
        }
    }

    @Test
    void clampSpeed_noChangeWhenBelowMax() {
        Vector3D v = new Vector3D(1, 0, 0);
        assertEquals(v, SimulationLoop.clampSpeed(v, 5.0));
    }

    @Test
    void clampSpeed_scalesDownWhenAboveMax() {
        Vector3D clamped = SimulationLoop.clampSpeed(new Vector3D(10, 0, 0), 5.0);
        assertEquals(5.0, clamped.magnitude(), 1e-9);
    }
}
