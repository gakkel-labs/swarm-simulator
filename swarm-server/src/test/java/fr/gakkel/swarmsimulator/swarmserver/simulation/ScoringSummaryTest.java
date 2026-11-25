package fr.gakkel.swarmsimulator.swarmserver.simulation;

import fr.gakkel.swarmsimulator.swarmserver.domain.SimulationEvent;
import fr.gakkel.swarmsimulator.swarmserver.domain.SimulationEvent.DroneLost;
import fr.gakkel.swarmsimulator.swarmserver.domain.SimulationEvent.EndReason;
import fr.gakkel.swarmsimulator.swarmserver.domain.SimulationEvent.LossCause;
import fr.gakkel.swarmsimulator.swarmserver.domain.SimulationEvent.RunEnded;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ScoringSummaryTest {

    private static SimulationResult result(EndReason reason, double endSeconds, int lostDrones) {
        List<SimulationEvent> events = new ArrayList<>();
        for (int lost = 0; lost < lostDrones; lost++) {
            events.add(new DroneLost(UUID.randomUUID(), LossCause.LOSS_RISK, 10.0));
        }
        RunEnded end = new RunEnded(reason, endSeconds);
        events.add(end);
        return new SimulationResult(end, events);
    }

    @Test
    void of_countsDetectionsAndAveragesTheirTimeAndTheLostDrones() {
        // Two detections (after 1 h and 3 h), two misses, 4 drones lost in all.
        List<SimulationResult> results = List.of(
                result(EndReason.TARGET_DETECTED, 3600, 0),
                result(EndReason.TARGET_DETECTED, 10800, 1),
                result(EndReason.LIMIT_TIME_REACHED, 57600, 3),
                result(EndReason.NO_DRONE_LEFT, 20000, 0));

        ScoringSummary summary = ScoringSummary.of(results);

        assertThat(summary.simulationCount()).isEqualTo(4);
        assertThat(summary.detectedCount()).isEqualTo(2);
        assertThat(summary.detectionRate()).isEqualTo(0.5);
        assertThat(summary.meanDetectionTimeSeconds()).hasValue(7200.0);
        assertThat(summary.meanLostDronesPerSimulation()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void of_noDetection_hasNoMeanDetectionTime() {
        ScoringSummary summary =
                ScoringSummary.of(List.of(result(EndReason.LIMIT_TIME_REACHED, 57600, 0)));

        assertThat(summary.detectedCount()).isZero();
        assertThat(summary.meanDetectionTimeSeconds()).isEmpty();
    }

    @Test
    void describe_givesPercentagesAndHoursForTheLog() {
        ScoringSummary summary = ScoringSummary.of(List.of(
                result(EndReason.TARGET_DETECTED, 3600, 0),
                result(EndReason.LIMIT_TIME_REACHED, 57600, 1),
                result(EndReason.LIMIT_TIME_REACHED, 57600, 0),
                result(EndReason.LIMIT_TIME_REACHED, 57600, 0)));

        assertThat(summary.describe()).isEqualTo("wreck detected 1/4 (25.0 %) | not detected 3 "
                + "(75.0 %) | mean detection time 1.0 h | drones lost 0.25 per simulation");
    }

    @Test
    void describe_noDetection_saysThereIsNoDetectionTime() {
        ScoringSummary summary =
                ScoringSummary.of(List.of(result(EndReason.LIMIT_TIME_REACHED, 57600, 0)));

        assertThat(summary.describe()).contains("mean detection time -");
    }

    @Test
    void of_noResult_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> ScoringSummary.of(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
