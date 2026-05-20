package fr.gakkel.swarmsimulator.swarmserver.simulation;

import fr.gakkel.swarmsimulator.swarmserver.domain.TriggerSource;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import fr.gakkel.swarmsimulator.swarmserver.domain.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimulationServiceTest {

    private World world;
    private SimulationService simulationService;

    @BeforeEach
    void setUp() {
        world = new World(100, 100, 50);
        simulationService = new SimulationService(world);
    }

    @Test
    void placeTarget_setsTargetOnWorld() {
        Vector3D position = new Vector3D(50, -30, 25);

        simulationService.placeTarget(position, TriggerSource.OPERATOR_CLICK);

        assertNotNull(world.target());
        assertEquals(position, world.target().position());
    }

    @Test
    void placeTarget_replacesExistingTarget() {
        Vector3D firstPosition  = new Vector3D(10, -10, 5);
        Vector3D secondPosition = new Vector3D(80, -50, 40);
        simulationService.placeTarget(firstPosition, TriggerSource.OPERATOR_CLICK);

        simulationService.placeTarget(secondPosition, TriggerSource.OPERATOR_CLICK);

        assertEquals(secondPosition, world.target().position());
    }

    @Test
    void placeTarget_nullPosition_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> simulationService.placeTarget(null, TriggerSource.OPERATOR_CLICK));
    }
}
