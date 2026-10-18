package io.github.praneetx.motionfit.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Verifies the Kotlin {@link GoalProgressCalculator} from Java, which also
 * demonstrates that the Java -> Kotlin call path works correctly.
 */
public class GoalProgressCalculatorTest {

    @Test
    public void percent_computesFifty() {
        assertEquals(50.0, GoalProgressCalculator.percent(5000, 10000), 0.0001);
    }

    @Test
    public void percent_isZeroWhenNothingDone() {
        assertEquals(0.0, GoalProgressCalculator.percent(0, 8000), 0.0001);
    }

    @Test
    public void percent_neverDividesByZero() {
        assertEquals(0.0, GoalProgressCalculator.percent(5000, 0), 0.0001);
        assertEquals(0.0, GoalProgressCalculator.percent(5000, -10), 0.0001);
    }

    @Test
    public void percent_canExceedHundred() {
        assertEquals(150.0, GoalProgressCalculator.percent(12000, 8000), 0.0001);
    }

    @Test
    public void percent_treatsNegativeActualAsZero() {
        assertEquals(0.0, GoalProgressCalculator.percent(-500, 8000), 0.0001);
    }

    @Test
    public void clampedPercent_neverAboveHundred() {
        assertEquals(100, GoalProgressCalculator.clampedPercent(12000, 8000));
        assertEquals(50, GoalProgressCalculator.clampedPercent(4000, 8000));
    }

    @Test
    public void roundedPercent_roundsToNearest() {
        assertEquals(100, GoalProgressCalculator.roundedPercent(999, 1000)); // 99.9  -> 100
        assertEquals(67, GoalProgressCalculator.roundedPercent(2, 3));       // 66.67 -> 67
    }

    @Test
    public void fraction_isClampedBetweenZeroAndOne() {
        assertEquals(0.5f, GoalProgressCalculator.fraction(4000, 8000), 0.0001f);
        assertEquals(1.0f, GoalProgressCalculator.fraction(12000, 8000), 0.0001f);
        assertEquals(0.0f, GoalProgressCalculator.fraction(0, 8000), 0.0001f);
    }

    @Test
    public void isReached_onlyWhenGoalIsPositiveAndMet() {
        assertTrue(GoalProgressCalculator.isReached(8000, 8000));
        assertTrue(GoalProgressCalculator.isReached(9000, 8000));
        assertFalse(GoalProgressCalculator.isReached(7999, 8000));
        assertFalse(GoalProgressCalculator.isReached(5, 0));
    }

    @Test
    public void formatPercent_hasOneDecimalPlace() {
        assertEquals("50.0%", GoalProgressCalculator.formatPercent(5000, 10000));
    }
}
