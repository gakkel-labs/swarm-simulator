package fr.gakkel.swarmsimulator.swarmserver.simulation;

public class CohesionMetric {

    private final int windowSize;
    private final double[] window;
    private int head = 0;
    private int count = 0;
    private volatile double smoothedSpread = 0.0;

    public CohesionMetric(int windowSize) {
        if (windowSize <= 0) throw new IllegalArgumentException("windowSize must be > 0");
        this.windowSize = windowSize;
        this.window = new double[windowSize];
    }

    public void record(double spread) {
        window[head] = spread;
        head = (head + 1) % windowSize;
        if (count < windowSize) count++;

        double sum = 0;
        for (int i = 0; i < count; i++) sum += window[i];
        smoothedSpread = sum / count;
    }

    public double smoothedSpreadM() {
        return smoothedSpread;
    }
}
