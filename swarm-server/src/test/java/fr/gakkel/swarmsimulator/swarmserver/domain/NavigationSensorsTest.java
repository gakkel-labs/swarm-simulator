package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NavigationSensorsTest {

    @Test
    void constructor_adrValues_keepsThem() {
        NavigationSensors sensors = new NavigationSensors(200.0, new ForwardSonar(100.0, 120.0));

        assertThat(sensors.altimeterMaxRangeMeters()).isEqualTo(200.0);
        assertThat(sensors.forwardSonar().rangeMeters()).isEqualTo(100.0);
        assertThat(sensors.forwardSonar().fieldOfViewDegrees()).isEqualTo(120.0);
    }

    @Test
    void constructor_zeroAltimeterRange_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new NavigationSensors(0.0, new ForwardSonar(100.0, 120.0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("altimeterMaxRangeMeters");
    }

    @Test
    void constructor_noForwardSonar_throwsNullPointerException() {
        assertThatThrownBy(() -> new NavigationSensors(200.0, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void forwardSonar_infiniteRange_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new ForwardSonar(Double.POSITIVE_INFINITY, 120.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rangeMeters");
    }

    @Test
    void forwardSonar_fieldOfViewAboveFullTurn_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new ForwardSonar(100.0, 400.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fieldOfViewDegrees");
    }
}
