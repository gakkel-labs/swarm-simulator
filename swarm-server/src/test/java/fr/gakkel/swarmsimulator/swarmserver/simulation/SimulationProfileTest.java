package fr.gakkel.swarmsimulator.swarmserver.simulation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulationProfileTest {

    @Test
    void demo_isRealTimeWithTheStepOfTheLiveLoop() {
        assertThat(SimulationProfile.DEMO.realTime()).isTrue();
        assertThat(SimulationProfile.DEMO.stepSeconds())
                .isEqualTo(1.0 / SimulationLoop.TICK_RATE_HZ);
    }

    @Test
    void scoring_runsAsFastAsPossibleWithOneSecondSteps() {
        // ADR-0011: at 1.5 m/s a drone moves 1.5 m per step, far less than the sonar band.
        assertThat(SimulationProfile.SCORING.realTime()).isFalse();
        assertThat(SimulationProfile.SCORING.stepSeconds()).isEqualTo(1.0);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY})
    void constructor_badStep_throwsIllegalArgumentException(double stepSeconds) {
        assertThatThrownBy(() -> new SimulationProfile("test", stepSeconds, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("step");
    }

    @Test
    void constructor_blankName_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new SimulationProfile(" ", 1.0, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
