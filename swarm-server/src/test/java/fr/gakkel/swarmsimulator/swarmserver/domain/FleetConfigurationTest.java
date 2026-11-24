package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FleetConfigurationTest {

    private static final SearchPatternSettings BOIDS =
            new SearchPatternSettings.Boids(BoidsConfig.builder().build());

    @Test
    void twoConfigurationsWithTheSameValues_areEqual() {
        // A value object: defined only by its values, like two 10-euro notes.
        FleetConfiguration first = new FleetConfiguration(20, BOIDS);
        FleetConfiguration second = new FleetConfiguration(20, BOIDS);

        assertEquals(first, second);
    }

    @Test
    void noDrone_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new FleetConfiguration(0, BOIDS));
    }

    @Test
    void noSearchPattern_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new FleetConfiguration(20, null));
    }

    @Test
    void boidsSettingsWithoutConfig_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new SearchPatternSettings.Boids(null));
    }
}
