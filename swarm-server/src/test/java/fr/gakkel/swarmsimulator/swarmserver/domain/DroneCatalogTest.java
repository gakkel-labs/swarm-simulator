package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DroneCatalogTest {

    private static final DroneSpecification SIDE_SCAN =
            TestDrones.detectionDrone("side-scan-a", 1.5, 50.0);
    private static final DroneSpecification FAST = TestDrones.detectionDrone("fast-b", 2.5, 30.0);

    @Test
    void specification_knownModel_returnsItsSpecification() {
        DroneCatalog catalog = new DroneCatalog(List.of(SIDE_SCAN, FAST));

        DroneSpecification found = catalog.specification("fast-b");

        assertThat(found).isEqualTo(FAST);
    }

    @Test
    void specification_unknownModel_throwsWithTheNameAndTheKnownModels() {
        DroneCatalog catalog = new DroneCatalog(List.of(SIDE_SCAN, FAST));

        assertThatThrownBy(() -> catalog.specification("mystery-drone"))
                .isInstanceOf(UnknownDroneModelException.class)
                .hasMessageContaining("'mystery-drone'")
                .hasMessageContaining("side-scan-a, fast-b");
    }

    @Test
    void models_twoModels_keepsTheCatalogOrder() {
        DroneCatalog catalog = new DroneCatalog(List.of(SIDE_SCAN, FAST));

        List<String> models = catalog.models();

        assertThat(models).containsExactly("side-scan-a", "fast-b");
    }

    @Test
    void defaultSpecification_twoModels_returnsTheFirstOne() {
        DroneCatalog catalog = new DroneCatalog(List.of(SIDE_SCAN, FAST));

        DroneSpecification first = catalog.defaultSpecification();

        assertThat(first).isEqualTo(SIDE_SCAN);
    }

    @Test
    void constructor_sameModelTwice_throwsIllegalArgumentException() {
        DroneSpecification sameNameOtherValues =
                TestDrones.detectionDrone("side-scan-a", 3.0, 20.0);

        assertThatThrownBy(() -> new DroneCatalog(List.of(SIDE_SCAN, sameNameOtherValues)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'side-scan-a'");
    }

    @Test
    void constructor_noModel_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new DroneCatalog(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
