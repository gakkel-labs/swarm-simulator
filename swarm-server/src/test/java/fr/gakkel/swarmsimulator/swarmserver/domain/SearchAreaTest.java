package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class SearchAreaTest {

    @Test
    void equals_sameSizes_returnsTrue() {
        // A value object: two search areas of 7 x 7 km are the same search area.
        assertThat(new SearchArea(7000, 7000)).isEqualTo(new SearchArea(7000, 7000));
    }

    @Test
    void areaSquareKilometers_sevenBySevenKilometers_returnsFortyNine() {
        SearchArea searchArea = new SearchArea(7000, 7000);

        double area = searchArea.areaSquareKilometers();

        assertThat(area).isCloseTo(49.0, within(1e-9));
    }

    @Nested
    class Constructor {

        @ParameterizedTest
        @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY})
        void badWidth_throwsIllegalArgumentException(double width) {
            assertThatThrownBy(() -> new SearchArea(width, 100))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("width");
        }

        @ParameterizedTest
        @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY})
        void badLength_throwsIllegalArgumentException(double length) {
            assertThatThrownBy(() -> new SearchArea(100, length))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("length");
        }
    }
}
