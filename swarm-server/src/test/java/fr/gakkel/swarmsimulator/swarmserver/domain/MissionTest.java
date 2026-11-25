package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MissionTest {

    private static final SearchArea SEARCH_AREA = new SearchArea(7000, 7000);

    @Test
    void equals_sameValues_returnsTrue() {
        assertThat(new Mission(SEARCH_AREA, 1000, LossRiskLevel.NONE))
                .isEqualTo(new Mission(SEARCH_AREA, 1000, LossRiskLevel.NONE));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY})
    void constructor_badSeabedDepth_throwsIllegalArgumentException(double seabedDepth) {
        assertThatThrownBy(() -> new Mission(SEARCH_AREA, seabedDepth, LossRiskLevel.NONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("seabed depth");
    }

    @Test
    void constructor_noSearchArea_throwsNullPointerException() {
        assertThatThrownBy(() -> new Mission(null, 1000, LossRiskLevel.NONE))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_noLossRisk_throwsNullPointerException() {
        assertThatThrownBy(() -> new Mission(SEARCH_AREA, 1000, null))
                .isInstanceOf(NullPointerException.class);
    }
}
