package com.example.fitnesstracker.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pure JVM tests for {@link ValidationUtils}.
 */
public class ValidationUtilsTest {

    private static final int MAX = 1_000_000;

    @Test
    public void parseNonNegative_acceptsZeroAndPositive() {
        ValidationUtils.ParsedInt zero = ValidationUtils.parseNonNegative("0", MAX);
        assertTrue(zero.isValid());
        assertEquals(0, zero.value);

        ValidationUtils.ParsedInt eightThousand = ValidationUtils.parseNonNegative("8000", MAX);
        assertTrue(eightThousand.isValid());
        assertEquals(8000, eightThousand.value);
    }

    @Test
    public void parseNonNegative_trimsSurroundingWhitespace() {
        ValidationUtils.ParsedInt parsed = ValidationUtils.parseNonNegative("  42  ", MAX);
        assertTrue(parsed.isValid());
        assertEquals(42, parsed.value);
    }

    @Test
    public void parseNonNegative_rejectsEmpty() {
        assertEquals(ValidationUtils.ValidationError.EMPTY,
                ValidationUtils.parseNonNegative(null, MAX).error);
        assertEquals(ValidationUtils.ValidationError.EMPTY,
                ValidationUtils.parseNonNegative("", MAX).error);
        assertEquals(ValidationUtils.ValidationError.EMPTY,
                ValidationUtils.parseNonNegative("   ", MAX).error);
    }

    @Test
    public void parseNonNegative_rejectsNegative() {
        assertEquals(ValidationUtils.ValidationError.NEGATIVE,
                ValidationUtils.parseNonNegative("-5", MAX).error);
    }

    @Test
    public void parseNonNegative_rejectsMalformed() {
        assertEquals(ValidationUtils.ValidationError.NOT_A_NUMBER,
                ValidationUtils.parseNonNegative("abc", MAX).error);
        assertEquals(ValidationUtils.ValidationError.NOT_A_NUMBER,
                ValidationUtils.parseNonNegative("1.5", MAX).error);
        assertEquals(ValidationUtils.ValidationError.NOT_A_NUMBER,
                ValidationUtils.parseNonNegative("1e3", MAX).error);
        assertEquals(ValidationUtils.ValidationError.NOT_A_NUMBER,
                ValidationUtils.parseNonNegative("12 34", MAX).error);
    }

    @Test
    public void parseNonNegative_rejectsTooLarge() {
        assertEquals(ValidationUtils.ValidationError.TOO_LARGE,
                ValidationUtils.parseNonNegative("1000001", MAX).error);
        assertEquals(ValidationUtils.ValidationError.TOO_LARGE,
                ValidationUtils.parseNonNegative("999999999999", MAX).error);
    }

    @Test
    public void parsePositive_rejectsZero() {
        assertEquals(ValidationUtils.ValidationError.ZERO_NOT_ALLOWED,
                ValidationUtils.parsePositive("0", MAX).error);
    }

    @Test
    public void parsePositive_acceptsPositiveValue() {
        ValidationUtils.ParsedInt parsed = ValidationUtils.parsePositive("30", MAX);
        assertTrue(parsed.isValid());
        assertEquals(30, parsed.value);
    }

    @Test
    public void parsePositive_rejectsNegative() {
        assertEquals(ValidationUtils.ValidationError.NEGATIVE,
                ValidationUtils.parsePositive("-1", MAX).error);
    }
}
