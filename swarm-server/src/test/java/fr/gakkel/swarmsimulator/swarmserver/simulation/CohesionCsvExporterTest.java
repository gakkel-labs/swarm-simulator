package fr.gakkel.swarmsimulator.swarmserver.simulation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class CohesionCsvExporterTest {

    @TempDir private Path outputDirectory;

    private Locale previousLocale;

    @BeforeEach
    void setUp() {
        previousLocale = Locale.getDefault();
    }

    @AfterEach
    void tearDown() {
        Locale.setDefault(previousLocale);
    }

    @Test
    void record_withFrenchDefaultLocale_writesDotDecimalSeparator() throws IOException {
        // A French system writes "1,50" with String.format, which adds a column to the CSV.
        Locale.setDefault(Locale.FRANCE);
        CohesionCsvExporter exporter = new CohesionCsvExporter(outputDirectory);

        exporter.record(1_000L, 1.5, 12.25);
        exporter.close();

        List<String> lines = Files.readAllLines(exporter.filePath());
        assertThat(lines).containsExactly("timestamp_ms,elapsed_s,cohesion_spread_m", "1000,1.50,12.25");
    }
}
