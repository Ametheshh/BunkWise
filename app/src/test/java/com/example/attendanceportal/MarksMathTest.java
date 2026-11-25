package com.example.attendanceportal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class MarksMathTest {
    @Test
    public void bandBoundaries() {
        assertEquals(MarksMath.Band.STRONG, MarksMath.band(50));
        assertEquals(MarksMath.Band.STRONG, MarksMath.band(40));
        assertEquals(MarksMath.Band.OKAY, MarksMath.band(39));
        assertEquals(MarksMath.Band.OKAY, MarksMath.band(25));
        assertEquals(MarksMath.Band.NEEDS_WORK, MarksMath.band(24));
        assertEquals(MarksMath.Band.NEEDS_WORK, MarksMath.band(0));
    }

    @Test
    public void summaryPicksAverageBestAndWeakest() {
        MarksMath.Summary s = MarksMath.summarize(Arrays.asList(47, 38, 50, 41));
        assertEquals(44.0, s.average, 1e-9);
        assertEquals(2, s.bestIndex);
        assertEquals(1, s.weakestIndex);
    }

    @Test
    public void summaryOfNothingIsNull() {
        assertNull(MarksMath.summarize(Collections.emptyList()));
        assertNull(MarksMath.summarize(null));
    }
}
