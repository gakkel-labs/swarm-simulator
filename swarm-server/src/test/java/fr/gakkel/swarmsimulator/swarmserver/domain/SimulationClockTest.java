package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimulationClockTest {

    @Test
    void newClock_startsAtZeroSeconds() {
        SimulationClock clock = new SimulationClock();

        assertEquals(0.0, clock.elapsedSeconds());
    }

    @Test
    void advance_addsTheStepDurationToTheElapsedTime() {
        SimulationClock clock = new SimulationClock();

        clock.advance(0.5);
        clock.advance(0.25);

        assertEquals(0.75, clock.elapsedSeconds());
    }

    @Test
    void advance_thirtyStepsOfOneThirtieth_makeOneSecond() {
        SimulationClock clock = new SimulationClock();

        for (int step = 0; step < 30; step++) {
            clock.advance(1.0 / 30);
        }

        assertEquals(1.0, clock.elapsedSeconds(), 1e-9);
    }

    @Test
    void advance_zeroDuration_throwsIllegalArgumentException() {
        SimulationClock clock = new SimulationClock();

        assertThrows(IllegalArgumentException.class, () -> clock.advance(0.0));
    }

    @Test
    void advance_negativeDuration_throwsIllegalArgumentException() {
        SimulationClock clock = new SimulationClock();

        assertThrows(IllegalArgumentException.class, () -> clock.advance(-0.1));
    }

    @Test
    void advance_notANumber_throwsIllegalArgumentException() {
        SimulationClock clock = new SimulationClock();

        assertThrows(IllegalArgumentException.class, () -> clock.advance(Double.NaN));
    }
}
