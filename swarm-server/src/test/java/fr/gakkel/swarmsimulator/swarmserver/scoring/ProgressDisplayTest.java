package fr.gakkel.swarmsimulator.swarmserver.scoring;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProgressDisplayTest {

    @Nested
    class Render {

        @Test
        void start_emptyBarAndFirstSpinnerFrame() {
            ProgressDisplay display = new ProgressDisplay(10_000, System.out);

            assertThat(display.render(0, 0.0, 0))
                    .isEqualTo("| [....................]     0/10000 (  0 %) 0 s");
        }

        @Test
        void thirtyPercent_fillsSixOfTheTwentyCells() {
            ProgressDisplay display = new ProgressDisplay(10_000, System.out);

            assertThat(display.render(3000, 84.4, 1))
                    .isEqualTo("/ [######..............]  3000/10000 ( 30 %) 84 s");
        }

        @Test
        void done_fullBar() {
            ProgressDisplay display = new ProgressDisplay(300, System.out);

            assertThat(display.render(300, 8.4, 2))
                    .isEqualTo("- [####################] 300/300 (100 %) 8 s");
        }

        @Test
        void spinner_turnsEveryFrameAndStartsAgainAfterFour() {
            ProgressDisplay display = new ProgressDisplay(4, System.out);

            assertThat(display.render(0, 0, 3)).startsWith("\\ ");
            assertThat(display.render(0, 0, 4)).startsWith("| ");
        }
    }

    @Test
    void finish_writesTheLastLineAndGoesToTheNextLine() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ProgressDisplay display =
                new ProgressDisplay(2, new PrintStream(bytes, true, StandardCharsets.UTF_8));
        display.start();

        display.simulationDone(1);
        display.simulationDone(2);
        display.finish();

        String output = bytes.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("2/2 (100 %)").endsWith(System.lineSeparator());
    }

    @Test
    void finish_withoutStart_showsZeroSecondsAndNoRedraw() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ProgressDisplay display =
                new ProgressDisplay(2, new PrintStream(bytes, true, StandardCharsets.UTF_8));

        display.finish();

        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .isEqualTo("\r| [....................] 0/2 (  0 %) 0 s" + System.lineSeparator());
    }

    @Test
    void constructor_noSimulation_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new ProgressDisplay(0, System.out))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
