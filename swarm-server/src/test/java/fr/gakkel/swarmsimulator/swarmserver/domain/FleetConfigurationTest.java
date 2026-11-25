package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FleetConfigurationTest {

    private static final SearchPatternSettings BOIDS =
            new SearchPatternSettings.Boids(BoidsConfig.builder().build());
    private static final DroneSpecification SIDE_SCAN =
            TestDrones.detectionDrone("side-scan-a", 1.5, 50.0);
    private static final DroneSpecification FAST = TestDrones.detectionDrone("fast-b", 2.5, 30.0);

    @Test
    void equals_sameValues_returnsTrue() {
        // A value object: defined only by its values, like two 10-euro notes.
        FleetConfiguration first = FleetConfiguration.ofOneModel(SIDE_SCAN, 20, BOIDS);
        FleetConfiguration second = FleetConfiguration.ofOneModel(SIDE_SCAN, 20, BOIDS);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void droneCount_twoModels_addsTheCountsOfEveryGroup() {
        // The example of #176: 6 side-scan drones and 4 faster drones with another sensor.
        FleetConfiguration fleet = new FleetConfiguration(
                List.of(new DroneGroup(SIDE_SCAN, 6), new DroneGroup(FAST, 4)), BOIDS);

        int droneCount = fleet.droneCount();

        assertThat(droneCount).isEqualTo(10);
    }

    @Test
    void fastestCruiseSpeedMetersPerSecond_twoModels_returnsTheFastestModel() {
        FleetConfiguration fleet = new FleetConfiguration(
                List.of(new DroneGroup(SIDE_SCAN, 6), new DroneGroup(FAST, 4)), BOIDS);

        double fastest = fleet.fastestCruiseSpeedMetersPerSecond();

        assertThat(fastest).isEqualTo(2.5);
    }

    @Test
    void longestSearchRangeMeters_twoModels_returnsTheLongestSensorRange() {
        FleetConfiguration fleet = new FleetConfiguration(
                List.of(new DroneGroup(SIDE_SCAN, 6), new DroneGroup(FAST, 4)), BOIDS);

        double longest = fleet.longestSearchRangeMeters();

        assertThat(longest).isEqualTo(50.0);
    }

    @Test
    void shortestEndurance_twoModels_returnsTheModelThatMustComeBackFirst() {
        DroneSpecification shortLived = new DroneSpecification("short-lived-c",
                FAST.role(), FAST.movement(), java.time.Duration.ofHours(8), FAST.searchSensor(),
                FAST.navigationSensors(), FAST.acousticModem());
        FleetConfiguration fleet = new FleetConfiguration(
                List.of(new DroneGroup(SIDE_SCAN, 6), new DroneGroup(shortLived, 4)), BOIDS);

        java.time.Duration shortest = fleet.shortestEndurance();

        assertThat(shortest).isEqualTo(java.time.Duration.ofHours(8));
    }

    @Test
    void droneGroups_listChangedAfterwards_keepsTheFirstValues() {
        List<DroneGroup> groups = new ArrayList<>(List.of(new DroneGroup(SIDE_SCAN, 6)));
        FleetConfiguration fleet = new FleetConfiguration(groups, BOIDS);

        groups.add(new DroneGroup(FAST, 4));

        assertThat(fleet.droneGroups()).containsExactly(new DroneGroup(SIDE_SCAN, 6));
    }

    @Test
    void constructor_noGroup_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new FleetConfiguration(List.of(), BOIDS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_sameModelInTwoGroups_throwsIllegalArgumentException() {
        // "6 x a, then 4 x a" is a mistake: it should be written "10 x a".
        List<DroneGroup> groups =
                List.of(new DroneGroup(SIDE_SCAN, 6), new DroneGroup(SIDE_SCAN, 4));

        assertThatThrownBy(() -> new FleetConfiguration(groups, BOIDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'side-scan-a'");
    }

    @Test
    void constructor_noSearchPattern_throwsNullPointerException() {
        assertThatThrownBy(() -> FleetConfiguration.ofOneModel(SIDE_SCAN, 20, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void droneGroup_noDrone_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new DroneGroup(SIDE_SCAN, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void droneGroup_negativeCount_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new DroneGroup(SIDE_SCAN, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void droneGroup_noSpecification_throwsNullPointerException() {
        assertThatThrownBy(() -> new DroneGroup(null, 6))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void boidsConstructor_noConfig_throwsNullPointerException() {
        assertThatThrownBy(() -> new SearchPatternSettings.Boids(null))
                .isInstanceOf(NullPointerException.class);
    }
}
