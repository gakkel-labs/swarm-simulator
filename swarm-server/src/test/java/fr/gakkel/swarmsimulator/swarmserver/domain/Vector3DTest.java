package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Vector3DTest {

    @Test
    void add_twoVectors_returnsSumComponentWise() {
        // Arrange
        var a = new Vector3D(1, 2, 3);
        var b = new Vector3D(4, 5, 6);

        // Act
        var result = a.add(b);

        // Assert
        assertEquals(new Vector3D(5, 7, 9), result);
    }

    @Test
    void subtract_twoVectors_returnsDifferenceComponentWise() {
        var a = new Vector3D(4, 5, 6);
        var b = new Vector3D(1, 2, 3);

        var result = a.subtract(b);

        assertEquals(new Vector3D(3, 3, 3), result);
    }

    @Test
    void scale_byFactor_returnsScaledVector() {
        var v = new Vector3D(1, 2, 3);

        var result = v.scale(2.0);

        assertEquals(new Vector3D(2, 4, 6), result);
    }

    @Test
    void magnitude_unitAlongX_returnsOne() {
        var v = new Vector3D(1, 0, 0);

        assertEquals(1.0, v.magnitude(), 1e-9);
    }

    @Test
    void magnitude_pythagorean345_returnsFive() {
        var v = new Vector3D(3, 4, 0);

        assertEquals(5.0, v.magnitude(), 1e-9);
    }

    @Test
    void normalize_nonZeroVector_resultHasMagnitudeOne() {
        var v = new Vector3D(3, 4, 0);

        var result = v.normalize();

        assertEquals(1.0, result.magnitude(), 1e-9);
    }

    @Test
    void normalize_zeroVector_returnsZero() {
        var result = Vector3D.ZERO.normalize();

        assertEquals(Vector3D.ZERO, result);
    }

    @Test
    void distanceTo_samePoint_returnsZero() {
        var v = new Vector3D(1, 2, 3);

        assertEquals(0.0, v.distanceTo(v), 1e-9);
    }

    @Test
    void distanceTo_pythagorean345_returnsFive() {
        var a = new Vector3D(0, 0, 0);
        var b = new Vector3D(3, 4, 0);

        assertEquals(5.0, a.distanceTo(b), 1e-9);
    }

    @Test
    void dot_perpendicularVectors_returnsZero() {
        var a = new Vector3D(1, 0, 0);
        var b = new Vector3D(0, 1, 0);

        assertEquals(0.0, a.dot(b), 1e-9);
    }

    @Test
    void dot_parallelVectors_returnsProduct() {
        var a = new Vector3D(2, 0, 0);
        var b = new Vector3D(3, 0, 0);

        assertEquals(6.0, a.dot(b), 1e-9);
    }

    @Test
    void zero_constant_allComponentsAreZero() {
        assertEquals(0.0, Vector3D.ZERO.x());
        assertEquals(0.0, Vector3D.ZERO.y());
        assertEquals(0.0, Vector3D.ZERO.z());
    }
}
