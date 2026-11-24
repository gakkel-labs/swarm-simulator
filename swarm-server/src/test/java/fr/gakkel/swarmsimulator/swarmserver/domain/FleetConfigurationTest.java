package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FleetConfigurationTest {

    private static final SearchPatternSettings BOIDS =
            new SearchPatternSettings.Boids(BoidsConfig.builder().build());

    @Test
    void equals_sameValues_returnsTrue() {
        // A value object: defined only by its values, like two 10-euro notes.
        FleetConfiguration first = new FleetConfiguration(20, BOIDS);
        FleetConfiguration second = new FleetConfiguration(20, BOIDS);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void constructor_noDrone_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new FleetConfiguration(0, BOIDS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_negativeDroneCount_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new FleetConfiguration(-1, BOIDS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_noSearchPattern_throwsNullPointerException() {
        assertThatThrownBy(() -> new FleetConfiguration(20, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void boidsConstructor_noConfig_throwsNullPointerException() {
        assertThatThrownBy(() -> new SearchPatternSettings.Boids(null))
                .isInstanceOf(NullPointerException.class);
    }
}
