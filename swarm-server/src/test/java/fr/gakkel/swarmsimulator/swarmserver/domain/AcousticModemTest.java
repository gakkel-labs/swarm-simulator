package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AcousticModemTest {

    @Test
    void constructor_adrValues_keepsThem() {
        AcousticModem modem = new AcousticModem(1000.0, 10.0, 0.15);

        assertThat(modem.rangeMeters()).isEqualTo(1000.0);
        assertThat(modem.messagePeriodSeconds()).isEqualTo(10.0);
        assertThat(modem.messageLossRate()).isEqualTo(0.15);
    }

    @Test
    void constructor_noLoss_isAccepted() {
        AcousticModem modem = new AcousticModem(1000.0, 10.0, 0.0);

        assertThat(modem.messageLossRate()).isZero();
    }

    @Test
    void constructor_negativeRange_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new AcousticModem(-1000.0, 10.0, 0.15))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rangeMeters");
    }

    @Test
    void constructor_zeroPeriod_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new AcousticModem(1000.0, 0.0, 0.15))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messagePeriodSeconds");
    }

    @Test
    void constructor_everyMessageLost_throwsIllegalArgumentException() {
        // With every message lost the drones never hear each other: not a modem.
        assertThatThrownBy(() -> new AcousticModem(1000.0, 10.0, 1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messageLossRate");
    }

    @Test
    void constructor_nanLossRate_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new AcousticModem(1000.0, 10.0, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messageLossRate");
    }
}
