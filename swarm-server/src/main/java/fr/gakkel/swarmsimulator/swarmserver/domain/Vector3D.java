package fr.gakkel.swarmsimulator.swarmserver.domain;

public record Vector3D(double x, double y, double z) {

    private static final double MIN_MAGNITUDE = 1e-10;

    public static final Vector3D ZERO = new Vector3D(0, 0, 0);

    public Vector3D add(Vector3D other) {
        return new Vector3D(x + other.x, y + other.y, z + other.z);
    }

    public Vector3D subtract(Vector3D other) {
        return new Vector3D(x - other.x, y - other.y, z - other.z);
    }

    public Vector3D scale(double factor) {
        return new Vector3D(x * factor, y * factor, z * factor);
    }

    public double magnitude() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public Vector3D normalize() {
        double mag = magnitude();
        if (mag < MIN_MAGNITUDE) return ZERO;
        return scale(1.0 / mag);
    }

    public double distanceTo(Vector3D other) {
        return subtract(other).magnitude();
    }

    public double dot(Vector3D other) {
        return x * other.x + y * other.y + z * other.z;
    }
}
