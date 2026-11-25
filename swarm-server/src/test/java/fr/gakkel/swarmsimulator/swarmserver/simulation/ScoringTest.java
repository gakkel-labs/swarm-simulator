package fr.gakkel.swarmsimulator.swarmserver.simulation;

import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.DroneSpecification;
import fr.gakkel.swarmsimulator.swarmserver.domain.FleetConfiguration;
import fr.gakkel.swarmsimulator.swarmserver.domain.LossRiskLevel;
import fr.gakkel.swarmsimulator.swarmserver.domain.Mission;
import fr.gakkel.swarmsimulator.swarmserver.domain.SearchArea;
import fr.gakkel.swarmsimulator.swarmserver.domain.SearchPatternSettings;
import fr.gakkel.swarmsimulator.swarmserver.domain.SimulationEvent.EndReason;
import fr.gakkel.swarmsimulator.swarmserver.domain.TestDrones;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoringTest {

    /** A scoring drone with a 10-minute endurance, so a simulation takes 600 steps. */
    private static final DroneSpecification SHORT_ENDURANCE_DRONE = withEndurance(
            TestDrones.scoringDetectionDrone(), Duration.ofMinutes(10));
    private static final FleetConfiguration FLEET = FleetConfiguration.ofOneModel(
            SHORT_ENDURANCE_DRONE, 10, new SearchPatternSettings.Boids(BoidsConfig.builder().build()));
    private static final Mission MISSION =
            new Mission(new SearchArea(3000, 5000), 1000, LossRiskLevel.NONE);

    private static DroneSpecification withEndurance(DroneSpecification drone, Duration endurance) {
        return new DroneSpecification(drone.model(), drone.role(), drone.movement(), endurance,
                drone.searchSensor(), drone.navigationSensors(), drone.acousticModem());
    }

    private static Scoring scoring(long masterSeed) {
        return new Scoring(FLEET, MISSION, Optional.empty(), masterSeed);
    }

    @Test
    void limitTime_isTheShortestEnduranceOfTheFleet() {
        assertThat(scoring(42L).limitTimeSeconds()).isEqualTo(600.0);
    }

    @Test
    void simulate_noWreck_endsAtTheLimitTimeWithScoringSteps() {
        SimulationResult result = scoring(42L).simulate(0);

        assertThat(result.end().reason()).isEqualTo(EndReason.LIMIT_TIME_REACHED);
        assertThat(result.end().atSeconds()).isEqualTo(600.0);
    }

    @Nested
    class Seeds {

        @Test
        void sameMasterSeedAndIndex_givesTheSameResult() {
            assertThat(scoring(42L).simulate(3)).isEqualTo(scoring(42L).simulate(3));
        }

        @Test
        void eachSimulationOfAScoring_hasItsOwnSeed() {
            Scoring scoring = scoring(42L);

            long distinctSeeds = IntStream.range(0, 300).mapToLong(scoring::seedOf).distinct()
                    .count();

            assertThat(distinctSeeds).isEqualTo(300);
        }

        @Test
        void anotherMasterSeed_givesOtherSeeds() {
            assertThat(scoring(42L).seedOf(0)).isNotEqualTo(scoring(43L).seedOf(0));
        }

        @Test
        void negativeIndex_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> scoring(42L).seedOf(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void constructor_wreckOutsideTheSearchArea_throwsIllegalArgumentException() {
        Optional<Vector3D> outside = Optional.of(new Vector3D(4000, -1000, 100));

        assertThatThrownBy(() -> new Scoring(FLEET, MISSION, outside, 42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wreck");
    }
}
