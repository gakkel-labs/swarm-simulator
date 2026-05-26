package fr.gakkel.swarmsimulator.swarmserver.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CohesionMetricTest {

    @Test
    void smoothedSigmaM_returnsZero_beforeAnyRecord() {
        CohesionMetric metric = new CohesionMetric(30);
        assertEquals(0.0, metric.smoothedSigmaM());
    }

    @Test
    void smoothedSigmaM_returnsSingleValue_afterOneRecord() {
        CohesionMetric metric = new CohesionMetric(30);
        metric.record(10.0);
        assertEquals(10.0, metric.smoothedSigmaM(), 1e-9);
    }

    @Test
    void smoothedSigmaM_averagesPartialWindow() {
        CohesionMetric metric = new CohesionMetric(4);
        metric.record(10.0);
        metric.record(20.0);
        assertEquals(15.0, metric.smoothedSigmaM(), 1e-9);
    }

    @Test
    void smoothedSigmaM_averagesFullWindow() {
        CohesionMetric metric = new CohesionMetric(3);
        metric.record(10.0);
        metric.record(20.0);
        metric.record(30.0);
        assertEquals(20.0, metric.smoothedSigmaM(), 1e-9);
    }

    @Test
    void smoothedSigmaM_dropsOldestOnOverflow() {
        CohesionMetric metric = new CohesionMetric(3);
        metric.record(10.0);
        metric.record(20.0);
        metric.record(30.0);
        metric.record(40.0); // drops 10
        assertEquals(30.0, metric.smoothedSigmaM(), 1e-9);
    }

    @Test
    void constructor_rejectsZeroWindowSize() {
        assertThrows(IllegalArgumentException.class, () -> new CohesionMetric(0));
    }
}
