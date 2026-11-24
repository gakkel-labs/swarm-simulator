package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DroneSpecificationTest {

    private static final DroneSpecification BATCH = TestDrones.batchDetectionDrone();

    @Test
    void equals_sameValues_returnsTrue() {
        // A value object: two specifications with the same values are the same specification.
        DroneSpecification first = TestDrones.batchDetectionDrone();
        DroneSpecification second = TestDrones.batchDetectionDrone();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void cruiseSpeedMetersPerSecond_batchDrone_readsTheMovementLimits() {
        double cruiseSpeed = BATCH.cruiseSpeedMetersPerSecond();

        assertThat(cruiseSpeed).isEqualTo(1.5);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void constructor_blankModel_throwsIllegalArgumentException(String model) {
        assertThatThrownBy(() -> withModel(model))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model");
    }

    @Test
    void constructor_nullModel_throwsNullPointerException() {
        assertThatThrownBy(() -> withModel(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_zeroEndurance_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> withEndurance(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endurance");
    }

    @Test
    void constructor_negativeEndurance_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> withEndurance(Duration.ofHours(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endurance");
    }

    @Test
    void constructor_noSearchSensor_throwsNullPointerException() {
        assertThatThrownBy(() -> new DroneSpecification("side-scan-drone", AgentType.EXPLORER,
                BATCH.movement(), BATCH.endurance(), null, BATCH.navigationSensors(),
                BATCH.acousticModem()))
                .isInstanceOf(NullPointerException.class);
    }

    private static DroneSpecification withModel(String model) {
        return new DroneSpecification(model, AgentType.EXPLORER, BATCH.movement(),
                BATCH.endurance(), BATCH.searchSensor(), BATCH.navigationSensors(),
                BATCH.acousticModem());
    }

    private static DroneSpecification withEndurance(Duration endurance) {
        return new DroneSpecification("side-scan-drone", AgentType.EXPLORER, BATCH.movement(),
                endurance, BATCH.searchSensor(), BATCH.navigationSensors(),
                BATCH.acousticModem());
    }
}
