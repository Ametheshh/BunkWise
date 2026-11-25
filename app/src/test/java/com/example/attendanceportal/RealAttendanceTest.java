package com.example.attendanceportal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Checks the rules for attendance typed in by the student. Uses no Android classes. */
public class RealAttendanceTest {

    private static RealAttendance.Profile profile() {
        RealAttendance.Profile p = new RealAttendance.Profile();
        p.usn = "NNM23CB999";
        p.name = "Test Student";
        return p;
    }

    private static RealAttendance.Entry entry(String name, boolean practical, int held, int attended) {
        RealAttendance.Entry e = new RealAttendance.Entry();
        e.name = name;
        e.practical = practical;
        e.conducted = held;
        e.attended = attended;
        return e;
    }

    private static List<RealAttendance.Entry> list(RealAttendance.Entry... entries) {
        List<RealAttendance.Entry> l = new ArrayList<>();
        for (RealAttendance.Entry e : entries) l.add(e);
        return l;
    }

    @Test
    public void validEntriesPass() {
        assertNull(RealAttendance.validate(profile(), list(
                entry("Cyber Forensics", false, 46, 40),
                entry("Cyber Forensics", true, 28, 26),          // same name is fine for theory + practical
                entry("New Subject", false, 0, 0))));
    }

    @Test
    public void attendedCannotExceedHeld() {
        assertNotNull(RealAttendance.validate(profile(), list(entry("Cloud Computing", false, 10, 11))));
    }

    @Test
    public void negativeAndEmptyAreRejected() {
        assertNotNull(RealAttendance.validate(profile(), list(entry("Cloud Computing", false, -1, 0))));
        assertNotNull(RealAttendance.validate(profile(), list(entry("  ", false, 5, 5))));
        assertNotNull(RealAttendance.validate(profile(), list()));
    }

    @Test
    public void duplicateSubjectIsRejected() {
        String err = RealAttendance.validate(profile(), list(
                entry("Cloud Computing", false, 10, 9),
                entry("cloud computing", false, 10, 9)));
        assertNotNull(err);
        assertTrue(err.contains("twice"));
    }

    @Test
    public void usnAndNameAreRequired() {
        RealAttendance.Profile p = profile();
        p.usn = "bad usn!";
        assertNotNull(RealAttendance.validate(p, list(entry("A", false, 1, 1))));
        p = profile();
        p.name = "";
        assertNotNull(RealAttendance.validate(p, list(entry("A", false, 1, 1))));
    }

    @Test
    public void idsNeverClashWithDemoIds() {
        long id = RealAttendance.studentIdFor("NNM23CB999");
        assertTrue(id > 1_000_000_000L);
        assertEquals(id, RealAttendance.studentIdFor("nnm23cb999 "));
        assertNotEquals(id, RealAttendance.studentIdFor("NNM23CB998"));
    }
}
