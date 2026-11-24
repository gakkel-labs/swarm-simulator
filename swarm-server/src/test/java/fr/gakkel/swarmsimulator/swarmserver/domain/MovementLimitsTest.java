package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovementLimitsTest {

    @Test
    void constructor_adrValues_keepsThem() {
        MovementLimits limits = new MovementLimits(1.5, 10.0, 0.5, 15.0, 6000.0);

        assertThat(limits.cruiseSpeedMetersPerSecond()).isEqualTo(1.5);
        assertThat(limits.maxTurnRateDegreesPerSecond()).isEqualTo(10.0);
        assertThat(limits.verticalSpeedMetersPerSecond()).isEqualTo(0.5);
        assertThat(limits.workingAltitudeMeters()).isEqualTo(15.0);
        assertThat(limits.maxDepthMeters()).isEqualTo(6000.0);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.5, Double.NaN, Double.POSITIVE_INFINITY})
    void constructor_badCruiseSpeed_throwsIllegalArgumentExceptionNamingTheValue(double speed) {
        assertThatThrownBy(() -> new MovementLimits(speed, 10.0, 0.5, 15.0, 6000.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cruiseSpeedMetersPerSecond");
    }

    @Test
    void constructor_negativeTurnRate_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new MovementLimits(1.5, -10.0, 0.5, 15.0, 6000.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxTurnRateDegreesPerSecond");
    }

    @Test
    void constructor_nanVerticalSpeed_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new MovementLimits(1.5, 10.0, Double.NaN, 15.0, 6000.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verticalSpeedMetersPerSecond");
    }

    @Test
    void constructor_zeroWorkingAltitude_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new MovementLimits(1.5, 10.0, 0.5, 0.0, 6000.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workingAltitudeMeters");
    }

    @Test
    void constructor_negativeMaxDepth_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new MovementLimits(1.5, 10.0, 0.5, 15.0, -6000.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxDepthMeters");
    }
}
