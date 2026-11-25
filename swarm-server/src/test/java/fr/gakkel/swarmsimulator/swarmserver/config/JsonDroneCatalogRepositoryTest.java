package fr.gakkel.swarmsimulator.swarmserver.config;

import fr.gakkel.swarmsimulator.swarmserver.domain.AgentType;
import fr.gakkel.swarmsimulator.swarmserver.domain.DroneCatalog;
import fr.gakkel.swarmsimulator.swarmserver.domain.DroneSpecification;
import fr.gakkel.swarmsimulator.swarmserver.domain.SearchSensor;
import fr.gakkel.swarmsimulator.swarmserver.domain.TestDrones;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonDroneCatalogRepositoryTest {

    // One drone with the scoring values of ADR-0011.
    private static final String SIDE_SCAN_DRONE = """
            {
              "model": "side-scan-drone",
              "role": "detection-drone",
              "movement": {
                "cruiseSpeedMetersPerSecond": 1.5,
                "maxTurnRateDegreesPerSecond": 10,
                "verticalSpeedMetersPerSecond": 0.5,
                "workingAltitudeMeters": 15,
                "maxDepthMeters": 6000
              },
              "enduranceHours": 16,
              "searchSensor": {
                "type": "side-scan-sonar",
                "rangePerSideMeters": 50,
                "nadirHalfWidthMeters": 15,
                "detectionProbability": 0.9,
                "fullProbabilityUntilMeters": 35,
                "falseContactsPerSquareKilometer": 0.5
              },
              "navigationSensors": {
                "altimeterMaxRangeMeters": 200,
                "forwardSonar": { "rangeMeters": 100, "fieldOfViewDegrees": 120 }
              },
              "acousticModem": {
                "rangeMeters": 1000,
                "messagePeriodSeconds": 10,
                "messageLossRate": 0.15
              }
            }
            """;

    @Test
    void load_oneSideScanDrone_buildsTheSpecificationOfTheAdr() {
        JsonDroneCatalogRepository repository = repositoryOf(catalogOf(SIDE_SCAN_DRONE));

        DroneCatalog catalog = repository.load();

        assertThat(catalog.specification("side-scan-drone"))
                .isEqualTo(TestDrones.scoringDetectionDrone());
    }

    @Test
    void load_twoModels_keepsBothInTheFileOrder() {
        String fastDrone = SIDE_SCAN_DRONE.replace("side-scan-drone", "fast-drone")
                .replace("\"cruiseSpeedMetersPerSecond\": 1.5",
                        "\"cruiseSpeedMetersPerSecond\": 2.5");
        JsonDroneCatalogRepository repository =
                repositoryOf(catalogOf(SIDE_SCAN_DRONE + "," + fastDrone));

        DroneCatalog catalog = repository.load();

        assertThat(catalog.models()).containsExactly("side-scan-drone", "fast-drone");
        assertThat(catalog.specification("fast-drone").cruiseSpeedMetersPerSecond())
                .isEqualTo(2.5);
    }

    @Test
    void load_fractionalEndurance_keepsTheMinutes() {
        String shortDrone = SIDE_SCAN_DRONE.replace("\"enduranceHours\": 16",
                "\"enduranceHours\": 1.5");
        JsonDroneCatalogRepository repository = repositoryOf(catalogOf(shortDrone));

        DroneSpecification specification = repository.load().specification("side-scan-drone");

        assertThat(specification.endurance()).isEqualTo(Duration.ofMinutes(90));
        assertThat(specification.role()).isEqualTo(AgentType.EXPLORER);
        assertThat(specification.searchSensor()).isInstanceOf(SearchSensor.SideScanSonar.class);
    }

    @Test
    void load_missingValue_throwsNamingTheModelAndTheField() {
        String noSpeed = SIDE_SCAN_DRONE.replace("\"cruiseSpeedMetersPerSecond\": 1.5,", "");
        JsonDroneCatalogRepository repository = repositoryOf(catalogOf(noSpeed));

        assertThatThrownBy(repository::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test-catalog.json")
                .hasMessageContaining("'side-scan-drone'")
                .hasMessageContaining("movement.cruiseSpeedMetersPerSecond");
    }

    @Test
    void load_textInsteadOfNumber_throwsNamingTheField() {
        String textSpeed = SIDE_SCAN_DRONE.replace("\"cruiseSpeedMetersPerSecond\": 1.5",
                "\"cruiseSpeedMetersPerSecond\": \"fast\"");
        JsonDroneCatalogRepository repository = repositoryOf(catalogOf(textSpeed));

        assertThatThrownBy(repository::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("movement.cruiseSpeedMetersPerSecond");
    }

    @Test
    void load_negativeSpeed_throwsNamingTheModelAndTheReason() {
        String negativeSpeed = SIDE_SCAN_DRONE.replace("\"cruiseSpeedMetersPerSecond\": 1.5",
                "\"cruiseSpeedMetersPerSecond\": -1.5");
        JsonDroneCatalogRepository repository = repositoryOf(catalogOf(negativeSpeed));

        assertThatThrownBy(repository::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'side-scan-drone'")
                .hasMessageContaining("cruiseSpeedMetersPerSecond must be positive");
    }

    @Test
    void load_unknownSensorType_throwsListingTheKnownTypes() {
        String camera = SIDE_SCAN_DRONE.replace("\"side-scan-sonar\"", "\"camera\"");
        JsonDroneCatalogRepository repository = repositoryOf(catalogOf(camera));

        assertThatThrownBy(repository::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'camera'")
                .hasMessageContaining("side-scan-sonar");
    }

    @Test
    void load_roleNotSupportedYet_throwsNamingTheRole() {
        // Drone carrier and specialised drones come with #35.
        String carrier = SIDE_SCAN_DRONE.replace("\"detection-drone\"", "\"drone-carrier\"");
        JsonDroneCatalogRepository repository = repositoryOf(catalogOf(carrier));

        assertThatThrownBy(repository::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'drone-carrier'")
                .hasMessageContaining("detection-drone");
    }

    @Test
    void load_sameModelTwice_throwsNamingTheModel() {
        JsonDroneCatalogRepository repository =
                repositoryOf(catalogOf(SIDE_SCAN_DRONE + "," + SIDE_SCAN_DRONE));

        assertThatThrownBy(repository::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'side-scan-drone'");
    }

    @Test
    void load_noDronesList_throwsIllegalStateException() {
        JsonDroneCatalogRepository repository = repositoryOf("{ \"models\": [] }");

        assertThatThrownBy(repository::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("drones");
    }

    @Test
    void load_brokenJson_throwsNamingTheSource() {
        JsonDroneCatalogRepository repository = repositoryOf("{ \"drones\": [ ");

        assertThatThrownBy(repository::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test-catalog.json");
    }

    @Test
    void load_missingClasspathResource_throwsNamingTheResource() {
        JsonDroneCatalogRepository repository =
                JsonDroneCatalogRepository.fromLocation("classpath:no-such-catalog.json");

        assertThatThrownBy(repository::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no-such-catalog.json");
    }

    @Test
    void load_demoCatalogOfTheServer_keepsTheValuesOfTheLiveDemo() {
        JsonDroneCatalogRepository repository =
                JsonDroneCatalogRepository.fromLocation(Settings.DEFAULT_DRONE_CATALOG);

        DroneSpecification first = repository.load().defaultSpecification();

        // 5 m/s and 10 m: the speed and detection radius of the demo before #176.
        assertThat(first.cruiseSpeedMetersPerSecond()).isEqualTo(5.0);
        assertThat(first.searchSensor().maxRangeMeters()).isEqualTo(10.0);
    }

    @Test
    void load_scoringCatalogOfTheServer_holdsTheValuesOfTheAdr() {
        JsonDroneCatalogRepository repository =
                JsonDroneCatalogRepository.fromLocation("classpath:drone-catalog-scoring.json");

        DroneCatalog catalog = repository.load();

        assertThat(catalog.specification("side-scan-drone"))
                .isEqualTo(TestDrones.scoringDetectionDrone());
    }

    @Test
    void load_demoAndScoringCatalogs_useTheSameModelNames() {
        // A fleet configuration is written the same way in both profiles (#177).
        DroneCatalog demo =
                JsonDroneCatalogRepository.fromLocation(Settings.DEFAULT_DRONE_CATALOG).load();
        DroneCatalog scoring = JsonDroneCatalogRepository
                .fromLocation("classpath:drone-catalog-scoring.json").load();

        assertThat(demo.models()).isEqualTo(scoring.models());
    }

    private static String catalogOf(String drones) {
        return "{ \"drones\": [" + drones + "] }";
    }

    private static JsonDroneCatalogRepository repositoryOf(String json) {
        return new JsonDroneCatalogRepository("test-catalog.json", () -> new StringReader(json));
    }
}
