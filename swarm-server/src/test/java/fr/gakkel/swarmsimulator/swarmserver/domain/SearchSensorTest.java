package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchSensorTest {

    private static final DetectionCurve ADR_CURVE = new DetectionCurve(0.9, 35.0);

    @Test
    void maxRangeMeters_sideScanSonar_returnsRangeOfOneSide() {
        SearchSensor sonar = new SearchSensor.SideScanSonar(50.0, 15.0, ADR_CURVE, 0.5);

        double range = sonar.maxRangeMeters();

        assertThat(range).isEqualTo(50.0);
    }

    @Test
    void sideScanSonar_negativeRange_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new SearchSensor.SideScanSonar(-50.0, 15.0, ADR_CURVE, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rangePerSideMeters");
    }

    @Test
    void sideScanSonar_nadirWiderThanRange_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new SearchSensor.SideScanSonar(50.0, 60.0, ADR_CURVE, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nadirHalfWidthMeters");
    }

    @Test
    void sideScanSonar_negativeNadir_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new SearchSensor.SideScanSonar(50.0, -1.0, ADR_CURVE, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nadirHalfWidthMeters");
    }

    @Test
    void sideScanSonar_fullProbabilityBeyondRange_throwsIllegalArgumentException() {
        DetectionCurve tooFar = new DetectionCurve(0.9, 70.0);

        assertThatThrownBy(() -> new SearchSensor.SideScanSonar(50.0, 15.0, tooFar, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fullProbabilityUntilMeters");
    }

    @Test
    void sideScanSonar_fullProbabilityInsideNadir_throwsIllegalArgumentException() {
        DetectionCurve insideNadir = new DetectionCurve(0.9, 10.0);

        assertThatThrownBy(() -> new SearchSensor.SideScanSonar(50.0, 15.0, insideNadir, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fullProbabilityUntilMeters");
    }

    @Test
    void sideScanSonar_negativeFalseContacts_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new SearchSensor.SideScanSonar(50.0, 15.0, ADR_CURVE, -0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("falseContactsPerSquareKilometer");
    }

    @Test
    void sideScanSonar_noCurve_throwsNullPointerException() {
        assertThatThrownBy(() -> new SearchSensor.SideScanSonar(50.0, 15.0, null, 0.5))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void detectionCurve_probabilityAboveOne_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new DetectionCurve(1.2, 35.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("detectionProbability");
    }

    @Test
    void detectionCurve_nanProbability_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new DetectionCurve(Double.NaN, 35.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("detectionProbability");
    }

    @Test
    void detectionCurve_zeroProbability_throwsIllegalArgumentException() {
        // A sensor that never detects anything is a mistake in the catalog, not a sensor.
        assertThatThrownBy(() -> new DetectionCurve(0.0, 35.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("detectionProbability");
    }
}
