package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObstacleTest {

    @Test
    void constructor_validArguments_buildsRecord() {
        var pos = new Vector3D(10, 20, 5);

        var obstacle = new Obstacle(pos, 3.5);

        assertEquals(pos, obstacle.position());
        assertEquals(3.5, obstacle.radius());
    }

    @Test
    void constructor_nullPosition_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new Obstacle(null, 1.0));
    }

    @Test
    void constructor_zeroRadius_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new Obstacle(Vector3D.ZERO, 0));
    }

    @Test
    void constructor_negativeRadius_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new Obstacle(Vector3D.ZERO, -1.5));
    }
}
