package fr.gakkel.swarmsimulator.swarmserver.scoring;

import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.DroneSpecification;
import fr.gakkel.swarmsimulator.swarmserver.domain.FleetConfiguration;
import fr.gakkel.swarmsimulator.swarmserver.domain.LossRiskLevel;
import fr.gakkel.swarmsimulator.swarmserver.domain.Mission;
import fr.gakkel.swarmsimulator.swarmserver.domain.SearchArea;
import fr.gakkel.swarmsimulator.swarmserver.domain.SearchPatternSettings;
import fr.gakkel.swarmsimulator.swarmserver.domain.TestDrones;
import fr.gakkel.swarmsimulator.swarmserver.simulation.Scoring;
import fr.gakkel.swarmsimulator.swarmserver.simulation.SimulationResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParallelScoringTest {

    private static final DroneSpecification SCORING_DRONE = TestDrones.scoringDetectionDrone();
    /** 5 minutes of endurance with a high loss risk: short runs whose results differ by seed. */
    private static final DroneSpecification SHORT_ENDURANCE_DRONE = new DroneSpecification(
            SCORING_DRONE.model(), SCORING_DRONE.role(), SCORING_DRONE.movement(),
            Duration.ofMinutes(5), SCORING_DRONE.searchSensor(), SCORING_DRONE.navigationSensors(),
            SCORING_DRONE.acousticModem());
    private static final Scoring SCORING = new Scoring(
            FleetConfiguration.ofOneModel(SHORT_ENDURANCE_DRONE, 5,
                    new SearchPatternSettings.Boids(BoidsConfig.builder().build())),
            new Mission(new SearchArea(2000, 2000), 500, LossRiskLevel.EXTREME),
            Optional.empty(), 42L);

    @Test
    void simulate_severalThreads_givesTheSameResultsInTheSameOrderAsOneByOne() {
        // Same seed, same result: the threads change the speed, never the results.
        List<SimulationResult> oneByOne = IntStream.range(0, 8).mapToObj(SCORING::simulate)
                .toList();

        List<SimulationResult> parallel = new ParallelScoring(4).simulate(SCORING, 8);

        assertThat(parallel).isEqualTo(oneByOne);
    }

    @Test
    void simulate_givesOneResultPerSimulation() {
        assertThat(new ParallelScoring(2).simulate(SCORING, 3)).hasSize(3);
    }

    @Test
    void constructor_noThread_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new ParallelScoring(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void simulate_noSimulation_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new ParallelScoring(2).simulate(SCORING, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaultThreadCount_leavesOneCoreForTheLiveDemo() {
        int cores = Runtime.getRuntime().availableProcessors();

        assertThat(ParallelScoring.defaultThreadCount()).isEqualTo(Math.max(1, cores - 1));
    }
}
