package fr.gakkel.swarmsimulator.swarmserver.server;

import fr.gakkel.swarmsimulator.swarmserver.domain.Vector3D;
import io.gakkel.swarm.contracts.v1.Vec3;

/**
 * The only place that converts between server coordinates and the NED coordinates of the gRPC
 * contract.
 *
 * <p>Server frame: X = East, Y = Up, Z = North. Y = 0 is the water surface, negative Y is
 * underwater.
 *
 * <p>NED frame (contract): X = North, Y = East, Z = Down. Z = 0 is the water surface, positive Z is
 * underwater. So a point on the sea floor at server Y = -depth has NED Z = depth.
 */
public final class CoordinateConversions {

    private CoordinateConversions() {}

    /** Server (East, Up, North) to NED (North, East, Down). */
    public static Vec3 serverToNed(Vector3D serverVector) {
        return Vec3.newBuilder()
                .setX((float) serverVector.z())
                .setY((float) serverVector.x())
                .setZ((float) -serverVector.y())
                .build();
    }

    /** NED (North, East, Down) to server (East, Up, North). Inverse of {@link #serverToNed}. */
    public static Vector3D nedToServer(Vec3 nedVector) {
        return new Vector3D(nedVector.getY(), -nedVector.getZ(), nedVector.getX());
    }
}
