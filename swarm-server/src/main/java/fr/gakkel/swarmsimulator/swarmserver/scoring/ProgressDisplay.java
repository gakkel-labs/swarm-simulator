package fr.gakkel.swarmsimulator.swarmserver.scoring;

import java.io.PrintStream;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A progress line in the terminal while a scoring runs, redrawn in place:
 * {@code / [######..............]  3000/10000 ( 30 %) 84 s}.
 *
 * <p>It costs almost nothing: one small thread wakes up 4 times per second to write one line,
 * and the simulations only increase a counter when they end.
 */
final class ProgressDisplay {

    private static final String SPINNER = "|/-\\";
    private static final int BAR_CELLS = 20;
    private static final long REFRESH_MILLISECONDS = 250;

    private final int simulationCount;
    private final PrintStream out;
    private final String countFormat;
    private final AtomicInteger doneCount = new AtomicInteger();
    private final ScheduledExecutorService refresher;
    private long startNanos = System.nanoTime();
    private int frame;

    ProgressDisplay(int simulationCount, PrintStream out) {
        if (simulationCount < 1) {
            throw new IllegalArgumentException(
                    "simulation count must be >= 1, got " + simulationCount);
        }
        this.simulationCount = simulationCount;
        this.out = out;
        // The count takes as many digits as the total, so the line keeps its width.
        this.countFormat = "%" + Integer.toString(simulationCount).length() + "d";
        this.refresher = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "scoring-progress");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Starts redrawing the line, 4 times per second. Call it only in a terminal: without it,
     * {@link #finish()} writes the last line once, which suits a log file.
     */
    void start() {
        startNanos = System.nanoTime();
        refresher.scheduleAtFixedRate(this::redraw, 0, REFRESH_MILLISECONDS,
                TimeUnit.MILLISECONDS);
    }

    /** Called by each simulation thread when one more simulation has ended. */
    void simulationDone(int doneSoFar) {
        doneCount.accumulateAndGet(doneSoFar, Math::max);
    }

    /** Stops redrawing, writes the last line and goes to the next line. */
    void finish() {
        refresher.shutdownNow();
        try {
            refresher.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        redraw();
        out.println();
    }

    private synchronized void redraw() {
        double elapsedSeconds = (System.nanoTime() - startNanos) / 1e9;
        out.print("\r" + render(doneCount.get(), elapsedSeconds, frame++));
        out.flush();
    }

    /** The line for {@code done} ended simulations, without the carriage return. */
    String render(int done, double elapsedSeconds, int spinnerFrame) {
        int filledCells = (int) ((long) done * BAR_CELLS / simulationCount);
        String bar = "#".repeat(filledCells) + ".".repeat(BAR_CELLS - filledCells);
        int percent = (int) ((long) done * 100 / simulationCount);
        return String.format(Locale.ROOT, "%c [%s] " + countFormat + "/%d (%3d %%) %d s",
                SPINNER.charAt(spinnerFrame % SPINNER.length()), bar, done, simulationCount,
                percent, (long) elapsedSeconds);
    }
}
