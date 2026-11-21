package fr.gakkel.swarmsimulator.swarmserver.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import io.gakkel.swarm.contracts.v1.Vec3;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CoordinateConversionsTest {

    private static final double FLOAT_TOLERANCE = 1e-4;

    @Test
    void serverToNed_pointUnderwater_mapsNorthEastDown() {
        // Arrange: 30 m east, 12 m below the surface, 40 m north
        Vector3D serverPosition = new Vector3D(30.0, -12.0, 40.0);

        // Act
        Vec3 nedPosition = CoordinateConversions.serverToNed(serverPosition);

        // Assert
        assertEquals(40.0f, nedPosition.getX(), "NED North = server Z");
        assertEquals(30.0f, nedPosition.getY(), "NED East = server X");
        assertEquals(12.0f, nedPosition.getZ(), "NED Down = -server Y");
    }

    @Test
    void nedToServer_pointUnderwater_mapsEastUpNorth() {
        // Arrange: 40 m north, 30 m east, 12 m down
        Vec3 nedPosition = Vec3.newBuilder().setX(40.0f).setY(30.0f).setZ(12.0f).build();

        // Act
        Vector3D serverPosition = CoordinateConversions.nedToServer(nedPosition);

        // Assert
        assertEquals(new Vector3D(30.0, -12.0, 40.0), serverPosition);
    }

    @ParameterizedTest
    @MethodSource("representativeServerPoints")
    void nedToServer_afterServerToNed_returnsOriginalPoint(Vector3D serverPosition) {
        // Act
        Vector3D roundTripPosition =
                CoordinateConversions.nedToServer(CoordinateConversions.serverToNed(serverPosition));

        // Assert
        assertEquals(serverPosition.x(), roundTripPosition.x(), FLOAT_TOLERANCE);
        assertEquals(serverPosition.y(), roundTripPosition.y(), FLOAT_TOLERANCE);
        assertEquals(serverPosition.z(), roundTripPosition.z(), FLOAT_TOLERANCE);
    }

    static Stream<Vector3D> representativeServerPoints() {
        return Stream.of(
                new Vector3D(0.0, 0.0, 0.0), // origin, at the water surface
                new Vector3D(-25.5, -80.25, 13.75), // deep, south-west quadrant
                new Vector3D(100.0, 5.0, -60.0), // above the surface
                new Vector3D(1.125, -0.5, 999.0)); // far north, just below the surface
    }
}
