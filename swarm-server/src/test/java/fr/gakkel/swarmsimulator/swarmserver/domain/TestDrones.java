package fr.gakkel.swarmsimulator.swarmserver.domain;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/** Drone specifications shared by the tests, so each test does not repeat every value. */
public final class TestDrones {

    /** The values of the live demo: 5 m/s and a 10 m detection reach, like before #176. */
    public static final DroneSpecification DEMO_DETECTION_DRONE =
            detectionDrone("demo-detection-drone", 5.0, 10.0);

    private TestDrones() {}

    /** The batch values of ADR-0011. */
    public static DroneSpecification batchDetectionDrone() {
        return new DroneSpecification("side-scan-drone", AgentType.EXPLORER,
                new MovementLimits(1.5, 10.0, 0.5, 15.0, 6000.0),
                Duration.ofHours(16),
                new SearchSensor.SideScanSonar(50.0, 15.0, new DetectionCurve(0.9, 35.0), 0.5),
                new NavigationSensors(200.0, new ForwardSonar(100.0, 120.0)),
                new AcousticModem(1000.0, 10.0, 0.15));
    }

    /** {@code count} demo detection drones, as the drone groups of a fleet configuration. */
    public static List<DroneGroup> demoDrones(int count) {
        return List.of(new DroneGroup(DEMO_DETECTION_DRONE, count));
    }

    /** The demo drone with another role, for the drone carrier and specialised drones (#35). */
    public static DroneSpecification demoDroneWithRole(AgentType role) {
        DroneSpecification demo = DEMO_DETECTION_DRONE;
        return new DroneSpecification("demo-" + role.name().toLowerCase(Locale.ROOT), role,
                demo.movement(), demo.endurance(), demo.searchSensor(), demo.navigationSensors(),
                demo.acousticModem());
    }

    /** A detection drone with its own model name, cruise speed and sonar range. */
    public static DroneSpecification detectionDrone(String model, double cruiseSpeed,
                                                    double sonarRange) {
        return new DroneSpecification(model, AgentType.EXPLORER,
                new MovementLimits(cruiseSpeed, 10.0, 0.5, 15.0, 6000.0),
                Duration.ofHours(16),
                new SearchSensor.SideScanSonar(sonarRange, 0.0,
                        new DetectionCurve(1.0, sonarRange), 0.0),
                new NavigationSensors(200.0, new ForwardSonar(100.0, 120.0)),
                new AcousticModem(1000.0, 10.0, 0.15));
    }
}
