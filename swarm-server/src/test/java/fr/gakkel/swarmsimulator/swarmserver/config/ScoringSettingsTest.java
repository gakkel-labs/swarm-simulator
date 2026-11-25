package fr.gakkel.swarmsimulator.swarmserver.config;

import fr.gakkel.swarmsimulator.swarmserver.domain.LossRiskLevel;
import fr.gakkel.swarmsimulator.swarmserver.domain.Mission;
import fr.gakkel.swarmsimulator.swarmserver.domain.SearchArea;
import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoringSettingsTest {

    /** The keys a scoring cannot do without, with the values of ADR-0011. */
    private static Map<String, String> requiredKeys() {
        Map<String, String> keys = new HashMap<>();
        keys.put("SWARM_SCORING_SEARCH_AREA_WIDTH", "7000");
        keys.put("SWARM_SCORING_SEARCH_AREA_LENGTH", "7000");
        keys.put("SWARM_SCORING_SEABED_DEPTH", "1000");
        keys.put("SWARM_SCORING_FLEET", "side-scan-drone:10");
        return keys;
    }

    private static ScoringSettings load(Map<String, String> env) {
        return ScoringSettings.load(env, new Properties());
    }

    @Test
    void requiredKeys_giveTheMissionAndTheFleet() {
        ScoringSettings settings = load(requiredKeys());

        assertThat(settings.mission())
                .isEqualTo(new Mission(new SearchArea(7000, 7000), 1000, LossRiskLevel.NONE));
        assertThat(settings.requestedFleet())
                .containsExactly(new Settings.RequestedDrones("side-scan-drone", 10));
    }

    @Test
    void defaults_threeHundredSimulationsWithTheScoringCatalogAndNoWreck() {
        ScoringSettings settings = load(requiredKeys());

        assertThat(settings.simulationCount()).isEqualTo(300);
        assertThat(settings.droneCatalogLocation())
                .isEqualTo("classpath:drone-catalog-scoring.json");
        assertThat(settings.wreckPosition()).isEmpty();
        assertThat(settings.seed()).isEqualTo(42L);
    }

    @Test
    void sharedKeys_lossRiskAndSeedAreTheSameAsForTheServer() {
        Map<String, String> env = requiredKeys();
        env.put("SWARM_LOSS_RISK_LEVEL", "high");
        env.put("SWARM_SEED", "7");

        ScoringSettings settings = load(env);

        assertThat(settings.mission().lossRisk()).isEqualTo(LossRiskLevel.HIGH);
        assertThat(settings.seed()).isEqualTo(7L);
    }

    @Nested
    class NoHiddenDefault {

        // A score computed on a search area or a seabed nobody chose would mislead the
        // operator: the scoring stops and names the missing key.
        @ParameterizedTest
        @ValueSource(strings = {"SWARM_SCORING_SEARCH_AREA_WIDTH",
            "SWARM_SCORING_SEARCH_AREA_LENGTH", "SWARM_SCORING_SEABED_DEPTH",
            "SWARM_SCORING_FLEET"})
        void missingKey_throwsIllegalArgumentExceptionNamingIt(String missingKey) {
            Map<String, String> env = requiredKeys();
            env.remove(missingKey);

            assertThatThrownBy(() -> load(env))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(missingKey);
        }

        @Test
        void propertyFile_canGiveTheRequiredKeys() {
            Properties props = new Properties();
            props.setProperty("swarm.scoring.search-area.width", "3000");
            props.setProperty("swarm.scoring.search-area.length", "5000");
            props.setProperty("swarm.scoring.seabed.depth", "800");
            props.setProperty("swarm.scoring.fleet", "side-scan-drone:4");

            ScoringSettings settings = ScoringSettings.load(Map.of(), props);

            assertThat(settings.mission().searchArea()).isEqualTo(new SearchArea(3000, 5000));
        }
    }

    @Nested
    class Wreck {

        @Test
        void wreckEastAndNorth_liesOnTheSeabed() {
            Map<String, String> env = requiredKeys();
            env.put("SWARM_SCORING_WRECK", "3500, 2000");

            ScoringSettings settings = load(env);

            // Backend frame (ADR-0007): X = East, Y = Up (underwater Y < 0), Z = North.
            assertThat(settings.wreckPosition()).contains(new Vector3D(3500, -1000, 2000));
        }

        @ParameterizedTest
        @ValueSource(strings = {"3500", "a,b", "1,2,3"})
        void badWreck_throwsIllegalArgumentException(String wreck) {
            Map<String, String> env = requiredKeys();
            env.put("SWARM_SCORING_WRECK", wreck);

            assertThatThrownBy(() -> load(env)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SWARM_SCORING_WRECK");
        }
    }

    @Test
    void simulationCount_lessThanOne_throwsIllegalArgumentException() {
        Map<String, String> env = requiredKeys();
        env.put("SWARM_SCORING_SIMULATION_COUNT", "0");

        assertThatThrownBy(() -> load(env)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("simulation count");
    }
}
