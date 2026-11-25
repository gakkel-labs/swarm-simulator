package fr.gakkel.swarmsimulator.swarmserver.scoring;

import fr.gakkel.swarmsimulator.swarmserver.simulation.Scoring;
import fr.gakkel.swarmsimulator.swarmserver.simulation.SimulationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/**
 * Runs the simulations of a scoring side by side, on a few threads of their own. It lives
 * outside the engine (ArchUnit): the engine has no thread, it only says what one simulation
 * does.
 *
 * <p>The results come back in the order of the simulations, whatever thread ran them: the same
 * master seed always gives the same list.
 */
public final class ParallelScoring {

    private static final Logger LOG = LoggerFactory.getLogger(ParallelScoring.class);

    private final int threadCount;

    public ParallelScoring(int threadCount) {
        if (threadCount < 1) {
            throw new IllegalArgumentException("thread count must be >= 1, got " + threadCount);
        }
        this.threadCount = threadCount;
    }

    /**
     * One thread per core, minus one: the live demo shares the computer and must stay smooth.
     */
    public static int defaultThreadCount() {
        return Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    }

    /**
     * Runs simulations {@code 0} to {@code simulationCount - 1} of {@code scoring} and returns
     * their results, in that order. Blocks until every simulation has ended.
     *
     * @throws IllegalStateException if a simulation failed (its error is the cause)
     */
    public List<SimulationResult> simulate(Scoring scoring, int simulationCount) {
        return simulate(scoring, simulationCount, doneSoFar -> { });
    }

    /**
     * Same as {@link #simulate(Scoring, int)}, and calls {@code onSimulationDone} with the number
     * of simulations ended so far each time one ends. It is called from the simulation threads:
     * keep it short. If it fails, the error is logged and the scoring goes on: showing the
     * progress must never cost a result.
     */
    public List<SimulationResult> simulate(Scoring scoring, int simulationCount,
                                           IntConsumer onSimulationDone) {
        Objects.requireNonNull(onSimulationDone, "onSimulationDone");
        if (simulationCount < 1) {
            throw new IllegalArgumentException(
                    "simulation count must be >= 1, got " + simulationCount);
        }
        AtomicInteger doneCount = new AtomicInteger();
        List<Callable<SimulationResult>> simulations = new ArrayList<>(simulationCount);
        for (int index = 0; index < simulationCount; index++) {
            int simulationIndex = index;
            simulations.add(() -> {
                SimulationResult result = scoring.simulate(simulationIndex);
                int doneSoFar = doneCount.incrementAndGet();
                try {
                    onSimulationDone.accept(doneSoFar);
                } catch (RuntimeException progressError) {
                    LOG.warn("Progress of the scoring not shown: {}", progressError.toString());
                }
                return result;
            });
        }
        AtomicInteger threadNumber = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount, runnable -> {
            Thread thread = new Thread(runnable, "scoring-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<SimulationResult>> futures = new ArrayList<>(simulationCount);
            for (Callable<SimulationResult> simulation : simulations) {
                futures.add(executor.submit(simulation));
            }
            // The first failure stops the scoring: the finally block cancels the others.
            List<SimulationResult> results = new ArrayList<>(simulationCount);
            for (Future<SimulationResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("scoring interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("a simulation of the scoring failed",
                    exception.getCause());
        } finally {
            executor.shutdownNow();
        }
    }
}
