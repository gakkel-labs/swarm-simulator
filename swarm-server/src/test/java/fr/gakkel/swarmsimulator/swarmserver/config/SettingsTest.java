package fr.gakkel.swarmsimulator.swarmserver.config;

import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsConfig;
import fr.gakkel.swarmsimulator.swarmserver.simulation.DiagnosticsConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettingsTest {

    private static Settings load(Map<String, String> env, Properties props) {
        return Settings.load(env, props);
    }

    private static Properties props(String... keyValues) {
        Properties p = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            p.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return p;
    }

    @Test
    void defaults_reproduceHistoricalBehaviour() {
        Settings s = load(Map.of(), new Properties());

        assertEquals(50051, s.port());
        assertEquals(20, s.agentCount());
        assertEquals(100, s.worldWidth());
        assertEquals(100, s.worldHeight());
        assertEquals(50, s.worldDepth());
        assertEquals(42L, s.seed());
        assertEquals(BoidsConfig.builder().build(), s.boids());
        assertEquals(DiagnosticsConfig.builder().build(), s.diagnostics());
    }

    @Test
    void propertyFile_overridesDefaults() {
        Settings s = load(Map.of(), props(
                "swarm.port", "50060",
                "swarm.agent-count", "8",
                "swarm.world.width", "200",
                "swarm.boids.max-speed", "9.0"));

        assertEquals(50060, s.port());
        assertEquals(8, s.agentCount());
        assertEquals(200, s.worldWidth());
        assertEquals(9.0, s.boids().maxSpeed());
        // untouched keys keep their builder default
        assertEquals(BoidsConfig.builder().build().separationWeight(), s.boids().separationWeight());
    }

    @Test
    void environmentVariable_winsOverPropertyFile() {
        Map<String, String> env = Map.of("SWARM_PORT", "50070");
        Properties props = props("swarm.port", "50060");

        assertEquals(50070, load(env, props).port());
    }

    @Test
    void diagnostics_thresholdsAreConfigurable() {
        Settings s = load(Map.of("SWARM_DIAG_STABILITY_SAMPLES", "5"), new Properties());
        assertEquals(5, s.diagnostics().stabilitySamples());
    }

    @Test
    void seed_numericIsParsed() {
        assertEquals(123L, load(Map.of("SWARM_SEED", "123"), new Properties()).seed());
    }

    @Test
    void seed_randomKeywordsDrawAValueWithoutThrowing() {
        // Two random draws should (overwhelmingly) differ — guards against a constant being returned.
        long first = load(Map.of("SWARM_SEED", "random"), new Properties()).seed();
        long second = load(Map.of("SWARM_SEED", "none"), new Properties()).seed();
        assertNotEquals(first, second);
    }

    @Test
    void blankValueFallsBackToDefault() {
        // an empty env value must not shadow the default with a parse error
        Map<String, String> env = new HashMap<>();
        env.put("SWARM_PORT", "   ");
        assertEquals(50051, load(env, new Properties()).port());
    }

    @Test
    void invalidInteger_throwsWithKeyInMessage() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> load(Map.of("SWARM_PORT", "abc"), new Properties()));
        assertEquals("SWARM_PORT='abc' is not a valid integer", ex.getMessage());
    }

    @Test
    void invalidDouble_throwsWithKeyInMessage() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> load(Map.of("SWARM_WORLD_WIDTH", "wide"), new Properties()));
        assertEquals("SWARM_WORLD_WIDTH='wide' is not a valid number", ex.getMessage());
    }

    @Test
    void invalidSeed_throwsWithGuidance() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> load(Map.of("SWARM_SEED", "later"), new Properties()));
        assertEquals("SWARM_SEED='later' must be an integer, 'random', or 'none'", ex.getMessage());
    }
}
