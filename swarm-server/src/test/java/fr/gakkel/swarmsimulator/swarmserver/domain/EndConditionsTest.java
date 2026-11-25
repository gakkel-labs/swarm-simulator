package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertThrows;

class EndConditionsTest {

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY})
    void missionEnd_limitTimeNotPositiveAndFinite_throwsIllegalArgumentException(
            double limitTimeSeconds) {
        assertThrows(IllegalArgumentException.class,
                () -> new EndConditions.MissionEnd(limitTimeSeconds));
    }
}
