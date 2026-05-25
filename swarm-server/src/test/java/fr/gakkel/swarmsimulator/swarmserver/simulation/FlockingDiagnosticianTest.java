package fr.gakkel.swarmsimulator.swarmserver.simulation;

import fr.gakkel.swarmsimulator.swarmserver.domain.Agent;
import fr.gakkel.swarmsimulator.swarmserver.domain.AgentType;
import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlockingDiagnosticianTest {

    private static final BoidsConfig BOIDS_CONFIG = BoidsConfig.builder().build();
    private static final DiagnosticsConfig DIAGNOSTICS_CONFIG = DiagnosticsConfig.builder()
            .stabilitySamples(3)
            .stabilityTolerance(0.20)
            .build();
    private static final double INTERVAL_SECONDS = 5.0;

    private World world;
    private FlockingDiagnostician diagnostician;

    @BeforeEach
    void setUp() {
        world = new World(100, 100, 50);
        diagnostician = new FlockingDiagnostician(BOIDS_CONFIG, DIAGNOSTICS_CONFIG);
    }

    @Test
    void flockingConfirmed_falseOnConstruction() {
        assertFalse(diagnostician.flockingConfirmed());
    }

    @Test
    void record_emptyWorld_doesNotCrash() {
        diagnostician.record(0, INTERVAL_SECONDS, world);

        assertFalse(diagnostician.flockingConfirmed());
    }

    @Test
    void record_stableSigmaOverEnoughSamples_confirmsFlocking() {
        addAgentsAtFixedPositions();

        for (int sample = 1; sample <= DIAGNOSTICS_CONFIG.stabilitySamples() + 1; sample++) {
            diagnostician.record(sample * (int) INTERVAL_SECONDS, INTERVAL_SECONDS, world);
        }

        assertTrue(diagnostician.flockingConfirmed(),
                "stable σ over " + DIAGNOSTICS_CONFIG.stabilitySamples() + " samples must confirm flocking");
    }

    @Test
    void record_sigmaSpikeAfterConfirmation_losesFlocking() {
        addAgentsAtFixedPositions();
        for (int sample = 1; sample <= DIAGNOSTICS_CONFIG.stabilitySamples() + 1; sample++) {
            diagnostician.record(sample * (int) INTERVAL_SECONDS, INTERVAL_SECONDS, world);
        }
        assertTrue(diagnostician.flockingConfirmed(), "precondition: flocking must be confirmed");

        scatterAgentsBeyondLostThreshold();
        diagnostician.record(100, INTERVAL_SECONDS, world);

        assertFalse(diagnostician.flockingConfirmed(),
                "σ above " + DIAGNOSTICS_CONFIG.flockingLostFraction() + " of smallest world dimension must lose flocking");
    }

    private void addAgentsAtFixedPositions() {
        world.addAgent(new Agent(UUID.randomUUID(), AgentType.EXPLORER, new Vector3D(50, -50, 25), Vector3D.ZERO));
        world.addAgent(new Agent(UUID.randomUUID(), AgentType.EXPLORER, new Vector3D(52, -50, 25), Vector3D.ZERO));
        world.addAgent(new Agent(UUID.randomUUID(), AgentType.EXPLORER, new Vector3D(50, -52, 25), Vector3D.ZERO));
    }

    private void scatterAgentsBeyondLostThreshold() {
        // smallest dim = depth = 50; flockingLostFraction default = 0.9 → σ must exceed 45.
        // Two opposite-corner agents on the same axis produce σ = halfDistance.
        for (Agent agent : List.copyOf(world.agents())) world.removeAgent(agent);
        world.addAgent(new Agent(UUID.randomUUID(), AgentType.EXPLORER, new Vector3D(0,   0, 0),  Vector3D.ZERO));
        world.addAgent(new Agent(UUID.randomUUID(), AgentType.EXPLORER, new Vector3D(100, -100, 50), Vector3D.ZERO));
    }
}
