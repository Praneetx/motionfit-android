package io.github.praneetx.motionfit.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** JVM tests for the small success/data/error load result. */
public class LoadResultTest {

    @Test
    public void successCarriesDataWithoutAnError() {
        LoadResult<String> result = LoadResult.success("steps");

        assertTrue(result.isSuccess());
        assertEquals("steps", result.getData());
        assertNull(result.getErrorMessage());
    }

    @Test
    public void errorCarriesAMessageWithoutData() {
        LoadResult<String> result = LoadResult.error("offline");

        assertFalse(result.isSuccess());
        assertNull(result.getData());
        assertEquals("offline", result.getErrorMessage());
    }
}
