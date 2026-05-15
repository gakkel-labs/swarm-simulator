package fr.gakkel.swarmsimulator.swarmserver.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosticsConfigTest {

    @Test
    void builder_defaultsAreValid() {
        assertDoesNotThrow(() -> DiagnosticsConfig.builder().build());
    }
}
