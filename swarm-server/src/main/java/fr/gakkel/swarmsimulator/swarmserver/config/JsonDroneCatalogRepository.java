package fr.gakkel.swarmsimulator.swarmserver.config;

import fr.gakkel.swarmsimulator.swarmserver.domain.AcousticModem;
import fr.gakkel.swarmsimulator.swarmserver.domain.AgentType;
import fr.gakkel.swarmsimulator.swarmserver.domain.DetectionCurve;
import fr.gakkel.swarmsimulator.swarmserver.domain.DroneCatalog;
import fr.gakkel.swarmsimulator.swarmserver.domain.DroneCatalogRepository;
import fr.gakkel.swarmsimulator.swarmserver.domain.DroneSpecification;
import fr.gakkel.swarmsimulator.swarmserver.domain.ForwardSonar;
import fr.gakkel.swarmsimulator.swarmserver.domain.MovementLimits;
import fr.gakkel.swarmsimulator.swarmserver.domain.NavigationSensors;
import fr.gakkel.swarmsimulator.swarmserver.domain.SearchSensor;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reads the drone catalog from a JSON file (an adapter of {@link DroneCatalogRepository}).
 *
 * <p>The domain records know nothing about JSON: this class reads the tree of values and builds
 * the records itself, so the JSON library stays out of the domain. Every error names the file,
 * the drone model and the field to fix. The format is described in
 * {@code docs/drone-catalog.md}.
 */
public final class JsonDroneCatalogRepository implements DroneCatalogRepository {

    /** Prefix of a location read from the classpath (inside the jar) instead of a file. */
    public static final String CLASSPATH_PREFIX = "classpath:";

    private static final String DETECTION_DRONE_ROLE = "detection-drone";
    private static final String SIDE_SCAN_SONAR_TYPE = "side-scan-sonar";
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** Opens the JSON text; a new reader for each {@link #load()}. */
    @FunctionalInterface
    interface ReaderOpener {
        Reader open() throws IOException;
    }

    private final String sourceName;
    private final ReaderOpener opener;

    JsonDroneCatalogRepository(String sourceName, ReaderOpener opener) {
        this.sourceName = Objects.requireNonNull(sourceName, "sourceName");
        this.opener = Objects.requireNonNull(opener, "opener");
    }

    /**
     * A repository reading {@code location}: {@code classpath:name.json} for a file inside the
     * jar, or a file path. A file path lets the operator add a drone model without a rebuild.
     */
    public static JsonDroneCatalogRepository fromLocation(String location) {
        Objects.requireNonNull(location, "location");
        if (location.startsWith(CLASSPATH_PREFIX)) {
            String resource = location.substring(CLASSPATH_PREFIX.length());
            return new JsonDroneCatalogRepository(location, () -> openClasspathResource(resource));
        }
        Path file = Path.of(location);
        return new JsonDroneCatalogRepository(location,
                () -> Files.newBufferedReader(file, StandardCharsets.UTF_8));
    }

    @Override
    public DroneCatalog load() {
        JsonNode root = readTree();
        JsonNode drones = root.get("drones");
        if (drones == null || !drones.isArray()) {
            throw error("the catalog must have a 'drones' list");
        }
        List<DroneSpecification> specifications = new ArrayList<>();
        for (JsonNode drone : drones) {
            specifications.add(readDrone(drone));
        }
        try {
            return new DroneCatalog(specifications);
        } catch (IllegalArgumentException exception) {
            throw error(exception.getMessage(), exception);
        }
    }

    private JsonNode readTree() {
        try (Reader reader = opener.open()) {
            return MAPPER.readTree(reader);
        } catch (IOException | UncheckedIOException exception) {
            throw error("cannot read the catalog: " + exception.getMessage(), exception);
        } catch (JacksonException exception) {
            throw error("not valid JSON: " + exception.getOriginalMessage(), exception);
        }
    }

    private DroneSpecification readDrone(JsonNode drone) {
        String model = requiredText(drone, "model", "a drone");
        String context = "drone '" + model + "'";
        try {
            return new DroneSpecification(
                    model,
                    readRole(drone, context),
                    readMovement(drone, context),
                    readEndurance(drone, context),
                    readSearchSensor(drone, context),
                    readNavigationSensors(drone, context),
                    readAcousticModem(drone, context));
        } catch (IllegalArgumentException exception) {
            // A domain check refused a value: say which drone it belongs to.
            throw error(context + ": " + exception.getMessage(), exception);
        }
    }

    private AgentType readRole(JsonNode drone, String context) {
        String role = requiredText(drone, "role", context);
        if (!DETECTION_DRONE_ROLE.equals(role)) {
            // Drone carrier and specialised drones come with #35.
            throw error(context + ": role '" + role + "' is not supported yet, use '"
                    + DETECTION_DRONE_ROLE + "'");
        }
        return AgentType.EXPLORER;
    }

    private MovementLimits readMovement(JsonNode drone, String context) {
        JsonNode movement = requiredObject(drone, "movement", context);
        return new MovementLimits(
                requiredNumber(movement, "movement", "cruiseSpeedMetersPerSecond", context),
                requiredNumber(movement, "movement", "maxTurnRateDegreesPerSecond", context),
                requiredNumber(movement, "movement", "verticalSpeedMetersPerSecond", context),
                requiredNumber(movement, "movement", "workingAltitudeMeters", context),
                requiredNumber(movement, "movement", "maxDepthMeters", context));
    }

    private Duration readEndurance(JsonNode drone, String context) {
        double hours = requiredNumber(drone, null, "enduranceHours", context);
        return Duration.ofSeconds(Math.round(hours * 3600));
    }

    private SearchSensor readSearchSensor(JsonNode drone, String context) {
        JsonNode sensor = requiredObject(drone, "searchSensor", context);
        String type = requiredText(sensor, "type", context + ", searchSensor");
        if (!SIDE_SCAN_SONAR_TYPE.equals(type)) {
            throw error(context + ": unknown searchSensor type '" + type + "', known types: "
                    + SIDE_SCAN_SONAR_TYPE);
        }
        return new SearchSensor.SideScanSonar(
                requiredNumber(sensor, "searchSensor", "rangePerSideMeters", context),
                requiredNumber(sensor, "searchSensor", "nadirHalfWidthMeters", context),
                new DetectionCurve(
                        requiredNumber(sensor, "searchSensor", "detectionProbability", context),
                        requiredNumber(sensor, "searchSensor", "fullProbabilityUntilMeters",
                                context)),
                requiredNumber(sensor, "searchSensor", "falseContactsPerSquareKilometer",
                        context));
    }

    private NavigationSensors readNavigationSensors(JsonNode drone, String context) {
        JsonNode sensors = requiredObject(drone, "navigationSensors", context);
        JsonNode forwardSonar = requiredObject(sensors, "forwardSonar", context);
        String forwardSonarPath = "navigationSensors.forwardSonar";
        return new NavigationSensors(
                requiredNumber(sensors, "navigationSensors", "altimeterMaxRangeMeters", context),
                new ForwardSonar(
                        requiredNumber(forwardSonar, forwardSonarPath, "rangeMeters", context),
                        requiredNumber(forwardSonar, forwardSonarPath, "fieldOfViewDegrees",
                                context)));
    }

    private AcousticModem readAcousticModem(JsonNode drone, String context) {
        JsonNode modem = requiredObject(drone, "acousticModem", context);
        return new AcousticModem(
                requiredNumber(modem, "acousticModem", "rangeMeters", context),
                requiredNumber(modem, "acousticModem", "messagePeriodSeconds", context),
                requiredNumber(modem, "acousticModem", "messageLossRate", context));
    }

    // ---- small readers: each error names the field with its full path (movement.maxDepthMeters)

    private JsonNode requiredObject(JsonNode parent, String field, String context) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isObject()) {
            throw error(context + ": missing object '" + field + "'");
        }
        return value;
    }

    private String requiredText(JsonNode parent, String field, String context) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isString() || value.stringValue().isBlank()) {
            throw error(context + ": missing text '" + field + "'");
        }
        return value.stringValue();
    }

    private double requiredNumber(JsonNode parent, String parentPath, String field,
                                  String context) {
        String path = parentPath == null ? field : parentPath + "." + field;
        JsonNode value = parent.get(field);
        if (value == null || !value.isNumber()) {
            throw error(context + ": missing number '" + path + "'");
        }
        return value.doubleValue();
    }

    private IllegalStateException error(String message) {
        return new IllegalStateException(sourceName + ": " + message);
    }

    private IllegalStateException error(String message, Exception cause) {
        return new IllegalStateException(sourceName + ": " + message, cause);
    }

    private static Reader openClasspathResource(String resource) throws IOException {
        InputStream stream =
                JsonDroneCatalogRepository.class.getClassLoader().getResourceAsStream(resource);
        if (stream == null) {
            throw new IOException("no resource '" + resource + "' on the classpath");
        }
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }
}
