package fr.gakkel.swarmsimulator.swarmserver.scoring;

import fr.gakkel.swarmsimulator.swarmserver.config.JsonDroneCatalogRepository;
import fr.gakkel.swarmsimulator.swarmserver.config.ScoringSettings;
import fr.gakkel.swarmsimulator.swarmserver.domain.DroneCatalog;
import fr.gakkel.swarmsimulator.swarmserver.domain.FleetConfiguration;
import fr.gakkel.swarmsimulator.swarmserver.domain.SimulationEvent.EndReason;
import fr.gakkel.swarmsimulator.swarmserver.simulation.Scoring;
import fr.gakkel.swarmsimulator.swarmserver.simulation.ScoringSummary;
import fr.gakkel.swarmsimulator.swarmserver.simulation.SimulationResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The command of Step 2: scores one fleet configuration on one mission, with no display. It
 * uses the same engine and the same {@link ParallelScoring} as the server will (the web call
 * comes with #175). The score itself comes with #82: for now it logs how the simulations ended
 * and a {@link ScoringSummary}.
 *
 * <p>The mission comes from {@code SWARM_SCORING_*} settings; there is no default search area,
 * seabed depth or fleet (see {@link ScoringSettings}).
 */
public final class ScoringCommand {

    private static final Logger LOG = LoggerFactory.getLogger(ScoringCommand.class);

    private ScoringCommand() {}

    public static void main(String[] args) {
        ScoringSettings settings = ScoringSettings.load();
        DroneCatalog catalog =
                JsonDroneCatalogRepository.fromLocation(settings.droneCatalogLocation()).load();
        FleetConfiguration fleet = settings.fleetConfiguration(catalog);
        Scoring scoring = new Scoring(fleet, settings.mission(), settings.wreckPosition(),
                settings.seed());
        int threadCount = ParallelScoring.defaultThreadCount();
        LOG.info("Scoring - {} simulations | {} drones | search area {} ({} km2) | seabed {} m "
                        + "| step {} s | limit time {} h | {} threads | seed {}",
                settings.simulationCount(), fleet.droneCount(), settings.mission().searchArea(),
                format(settings.mission().searchArea().areaSquareKilometers()),
                settings.mission().seabedDepthMeters(), Scoring.PROFILE.stepSeconds(),
                format(scoring.limitTimeSeconds() / 3600), threadCount, settings.seed());

        long startNanos = System.nanoTime();
        ProgressDisplay progress = new ProgressDisplay(settings.simulationCount(), System.out);
        // The line is redrawn only in a terminal: a log file gets the last line once.
        if (System.console() != null) {
            progress.start();
        }
        List<SimulationResult> results;
        try {
            results = new ParallelScoring(threadCount).simulate(scoring,
                    settings.simulationCount(), progress::simulationDone);
        } finally {
            progress.finish();
        }
        double computationSeconds = (System.nanoTime() - startNanos) / 1e9;

        LOG.info("Scoring done in {} s - simulations ended by: {}", format(computationSeconds),
                countByEndReason(results));
        LOG.info("Scoring summary - {}", ScoringSummary.of(results).describe());
    }

    static Map<EndReason, Long> countByEndReason(List<SimulationResult> results) {
        Map<EndReason, Long> counts = new EnumMap<>(EndReason.class);
        for (SimulationResult result : results) {
            counts.merge(result.end().reason(), 1L, Long::sum);
        }
        return counts;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
