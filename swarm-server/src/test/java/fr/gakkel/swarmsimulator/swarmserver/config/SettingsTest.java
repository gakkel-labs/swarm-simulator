package fr.gakkel.swarmsimulator.swarmserver.config;

import fr.gakkel.swarmsimulator.swarmserver.domain.BoidsConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.DroneCatalog;
import fr.gakkel.swarmsimulator.swarmserver.domain.DroneGroup;
import fr.gakkel.swarmsimulator.swarmserver.domain.DroneSpecification;
import fr.gakkel.swarmsimulator.swarmserver.domain.FleetConfiguration;
import fr.gakkel.swarmsimulator.swarmserver.domain.PredatorConfig;
import fr.gakkel.swarmsimulator.swarmserver.domain.SearchPatternSettings;
import fr.gakkel.swarmsimulator.swarmserver.domain.TestDrones;
import fr.gakkel.swarmsimulator.swarmserver.domain.UnknownDroneModelException;
import fr.gakkel.swarmsimulator.swarmserver.simulation.DiagnosticsConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static final DroneSpecification SIDE_SCAN =
            TestDrones.detectionDrone("side-scan-a", 1.5, 50.0);
    private static final DroneSpecification FAST = TestDrones.detectionDrone("fast-b", 2.5, 30.0);
    private static final DroneCatalog CATALOG = new DroneCatalog(List.of(SIDE_SCAN, FAST));

    @Test
    void fleetConfiguration_noFleetSet_usesTheFirstCatalogModelTimesTheAgentCount() {
        // Without swarm.fleet the live demo keeps working as before: SWARM_AGENT_COUNT drones.
        Settings settings = load(Map.of(), props("swarm.agent-count", "8"));

        FleetConfiguration fleet = settings.fleetConfiguration(CATALOG);

        assertThat(fleet).isEqualTo(FleetConfiguration.ofOneModel(SIDE_SCAN, 8,
                new SearchPatternSettings.Boids(settings.boids())));
    }

    @Test
    void fleetConfiguration_twoModelsSet_mixesThemInTheGivenOrder() {
        Settings settings = load(Map.of("SWARM_FLEET", "fast-b:4, side-scan-a:6"),
                new Properties());

        FleetConfiguration fleet = settings.fleetConfiguration(CATALOG);

        assertThat(fleet.droneGroups())
                .containsExactly(new DroneGroup(FAST, 4), new DroneGroup(SIDE_SCAN, 6));
    }

    @Test
    void fleetConfiguration_fleetInPropertyFile_isRead() {
        Settings settings = load(Map.of(), props("swarm.fleet", "fast-b:3"));

        FleetConfiguration fleet = settings.fleetConfiguration(CATALOG);

        assertThat(fleet.droneGroups()).containsExactly(new DroneGroup(FAST, 3));
    }

    @Test
    void fleetConfiguration_modelMissingFromTheCatalog_throwsWithTheKnownModels() {
        Settings settings = load(Map.of("SWARM_FLEET", "mystery-drone:4"), new Properties());

        assertThatThrownBy(() -> settings.fleetConfiguration(CATALOG))
                .isInstanceOf(UnknownDroneModelException.class)
                .hasMessageContaining("'mystery-drone'")
                .hasMessageContaining("side-scan-a, fast-b");
    }

    @Test
    void load_fleetWithoutCount_throwsNamingTheExpectedFormat() {
        Map<String, String> env = Map.of("SWARM_FLEET", "side-scan-a");

        assertThatThrownBy(() -> load(env, new Properties()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SWARM_FLEET")
                .hasMessageContaining("model:count");
    }

    @Test
    void load_fleetWithTextCount_throwsNamingTheExpectedFormat() {
        Map<String, String> env = Map.of("SWARM_FLEET", "side-scan-a:six");

        assertThatThrownBy(() -> load(env, new Properties()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SWARM_FLEET")
                .hasMessageContaining("'six'");
    }

    @Test
    void load_fleetWithZeroDrones_throwsIllegalArgumentException() {
        Map<String, String> env = Map.of("SWARM_FLEET", "side-scan-a:0");

        assertThatThrownBy(() -> load(env, new Properties()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SWARM_FLEET");
    }

    @Test
    void load_noCatalogSet_usesTheDemoCatalogOfTheJar() {
        Settings settings = load(Map.of(), new Properties());

        assertThat(settings.droneCatalogLocation()).isEqualTo("classpath:drone-catalog-demo.json");
    }

    @Test
    void load_catalogFileSet_keepsItsPath() {
        Settings settings = load(Map.of("SWARM_DRONE_CATALOG", "C:/gakkel/my-drones.json"),
                new Properties());

        assertThat(settings.droneCatalogLocation()).isEqualTo("C:/gakkel/my-drones.json");
    }

    @Test
    void propertyFile_overridesDefaults() {
        Settings s = load(Map.of(), props(
                "swarm.port", "50060",
                "swarm.agent-count", "8",
                "swarm.world.width", "200",
                "swarm.boids.cohesion-weight", "9.0"));

        assertEquals(50060, s.port());
        assertEquals(8, s.agentCount());
        assertEquals(200, s.worldWidth());
        assertEquals(9.0, s.boids().cohesionWeight());
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
    void agentCountBelowOne_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> load(Map.of("SWARM_AGENT_COUNT", "0"), new Properties()));
        assertEquals("agent count must be >= 1 (got 0)", ex.getMessage());
    }

    @Test
    void invalidSeed_throwsWithGuidance() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> load(Map.of("SWARM_SEED", "later"), new Properties()));
        assertEquals("SWARM_SEED='later' must be an integer, 'random', or 'none'", ex.getMessage());
    }

    @Test
    void predator_speedAndEatRadiusAreConfigurable() {
        Settings settings = load(Map.of("SWARM_PREDATOR_SPEED", "4.5"),
                props("swarm.predator.eat-radius", "2.5"));

        assertEquals(new PredatorConfig(4.5, 2.5), settings.predator());
    }

    @Test
    void predator_zeroSpeed_throwsWithTheVariableName() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> load(Map.of("SWARM_PREDATOR_SPEED", "0"), new Properties()));

        assertTrue(exception.getMessage().contains("SWARM_PREDATOR_SPEED"));
    }

    @Test
    void predator_defaultsMatchTheHistoricalValues() {
        assertEquals(PredatorConfig.defaults(), load(Map.of(), new Properties()).predator());
    }
}
