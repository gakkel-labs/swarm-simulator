package fr.gakkel.swarmsimulator.swarmserver.simulation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CohesionCsvExporter implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(CohesionCsvExporter.class);

    private final BufferedWriter writer;
    private final Path filePath;

    public CohesionCsvExporter(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        filePath = outputDir.resolve("cohesion-" + System.currentTimeMillis() + ".csv");
        writer = Files.newBufferedWriter(filePath);
        writer.write("timestamp_ms,elapsed_s,cohesion_spread_m");
        writer.newLine();
        writer.flush();
        LOG.info("Cohesion CSV export started: {}", filePath);
    }

    public void record(long timestampMs, double elapsedS, double spreadM) {
        try {
            writer.write(timestampMs + "," + String.format("%.2f", elapsedS) + "," + String.format("%.2f", spreadM));
            writer.newLine();
        } catch (IOException e) {
            LOG.warn("CSV write failed", e);
        }
    }

    public Path filePath() {
        return filePath;
    }

    @Override
    public void close() {
        try {
            writer.close();
            LOG.info("Cohesion CSV export closed: {}", filePath);
        } catch (IOException e) {
            LOG.warn("Failed to close CSV writer", e);
        }
    }
}
