package io.github.praneetx.motionfit.repositories;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.sqlite.SQLiteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import io.github.praneetx.motionfit.logic.DayActivity;
import io.github.praneetx.motionfit.utils.LoadResult;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.util.List;

/**
 * Task 8: a failed Progress read must not be indistinguishable from a genuinely
 * empty period. The legacy best-effort method keeps returning zero-filled data.
 */
@RunWith(AndroidJUnit4.class)
public class SummaryRepositoryResultTest {

    private Context context;

    @Before
    public void resetData() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(new DataResetRepository(context).clearAllData().isSuccess());
    }

    @Test
    public void emptyPeriodIsASuccessfulZeroFilledSeries() {
        LocalDate today = LocalDate.now();
        LoadResult<List<DayActivity>> result =
                new SummaryRepository(context).loadDailySeriesResult(today, today);

        assertTrue(result.isSuccess());
        assertNull(result.getErrorMessage());
        assertNotNull(result.getData());
        assertEquals(1, result.getData().size());
        assertEquals(0, result.getData().get(0).getSteps());
    }

    @Test
    public void readFailureIsAnErrorAndLegacyReadStillZeroFills() {
        LocalDate today = LocalDate.now();
        SummaryRepository failing = new SummaryRepository(context) {
            @Override
            List<DayActivity> loadRecorded(LocalDate start, LocalDate end) {
                throw new SQLiteException("simulated read failure");
            }
        };

        LoadResult<List<DayActivity>> result =
                failing.loadDailySeriesResult(today, today);
        assertFalse(result.isSuccess());
        assertNull(result.getData());
        assertNotNull(result.getErrorMessage());

        List<DayActivity> legacy = failing.getDailySeries(today, today);
        assertEquals("legacy read keeps its zero-filled contract", 1, legacy.size());
        assertEquals(0, legacy.get(0).getSteps());
    }
}
