package fr.gakkel.swarmsimulator.swarmserver.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LossRiskLevelTest {

    private static final double SECONDS_PER_DAY = 24 * 3600;

    @Nested
    class LossProbabilityPerDay {

        @Test
        void levels_giveTheAgreedChanceOfLosingADroneInOneDayOfDiving() {
            assertEquals(0.0, LossRiskLevel.NONE.lossProbabilityPerDay());
            assertEquals(0.002, LossRiskLevel.LOW.lossProbabilityPerDay());
            assertEquals(0.02, LossRiskLevel.MEDIUM.lossProbabilityPerDay());
            assertEquals(0.05, LossRiskLevel.HIGH.lossProbabilityPerDay());
            assertEquals(0.15, LossRiskLevel.EXTREME.lossProbabilityPerDay());
        }

        @Test
        void eachLevel_isRiskierThanThePreviousOne() {
            LossRiskLevel[] levels = LossRiskLevel.values();
            for (int index = 1; index < levels.length; index++) {
                assertTrue(levels[index].lossProbabilityPerDay()
                        > levels[index - 1].lossProbabilityPerDay(), levels[index].name());
            }
        }
    }

    @Nested
    class LossProbabilityDuring {

        @ParameterizedTest
        @EnumSource(LossRiskLevel.class)
        void oneDay_givesTheProbabilityPerDay(LossRiskLevel level) {
            assertEquals(level.lossProbabilityPerDay(),
                    level.lossProbabilityDuring(SECONDS_PER_DAY), 1e-12);
        }

        @Test
        void twoDays_isTheChanceOfLosingTheDroneOnEitherDay() {
            // Surviving two days = surviving the first day and the second: 0.98 x 0.98.
            double survivalTwoDays = 0.98 * 0.98;

            assertEquals(1 - survivalTwoDays,
                    LossRiskLevel.MEDIUM.lossProbabilityDuring(2 * SECONDS_PER_DAY), 1e-12);
        }

        @Test
        void oneSimulationStep_isTinyButNotZero() {
            double probability = LossRiskLevel.LOW.lossProbabilityDuring(1.0 / 30);

            assertTrue(probability > 0, "the risk must not vanish in a short step");
            assertTrue(probability < 1e-7, "got " + probability);
        }

        @Test
        void none_neverLosesADrone() {
            assertEquals(0.0, LossRiskLevel.NONE.lossProbabilityDuring(10 * SECONDS_PER_DAY));
        }

        @Test
        void negativeDuration_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> LossRiskLevel.LOW.lossProbabilityDuring(-1.0));
        }
    }

    @Nested
    class FromSetting {

        @Test
        void lowerCaseName_givesTheLevel() {
            assertEquals(LossRiskLevel.EXTREME, LossRiskLevel.fromSetting("extreme"));
        }

        @Test
        void spacesAndUpperCase_areAccepted() {
            assertEquals(LossRiskLevel.MEDIUM, LossRiskLevel.fromSetting(" Medium "));
        }

        @Test
        void unknownName_throwsWithTheAllowedValues() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> LossRiskLevel.fromSetting("huge"));

            assertEquals("loss risk level 'huge' is unknown; expected one of: "
                    + "none, low, medium, high, extreme", exception.getMessage());
        }
    }
}
