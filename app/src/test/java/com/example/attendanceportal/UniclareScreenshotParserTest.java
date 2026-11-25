package com.example.attendanceportal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.attendanceportal.UniclareScreenshotParser.Line;
import com.example.attendanceportal.UniclareScreenshotParser.Result;
import com.example.attendanceportal.UniclareScreenshotParser.Row;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/** Line positions follow a real 738px-wide screenshot of Uniclare's Attendance Summary page. */
public class UniclareScreenshotParserTest {

    private static Line l(String text, int left, int top, int right) {
        return new Line(text, left, top, right, top + 32);
    }

    /** The top of the page: name card, table header, five full subjects and one cut off at the bottom. */
    private static List<Line> firstScreen() {
        return new ArrayList<>(Arrays.asList(
                l("Attendance", 95, 136, 263), l("Attendance Summary", 319, 136, 657),
                l("TEST STUDENT", 65, 281, 392),
                l("Reg. No: NNM00XX000", 65, 333, 346),
                l("Degree: BTCY23", 65, 372, 265),
                l("Semester: -", 65, 411, 205),
                l("Subject", 53, 537, 145), l("TCC", 381, 537, 431), l("TCA", 477, 537, 526), l("%", 585, 537, 608),
                l("Cyber Forensics -", 53, 604, 277), l("CB3002-1 - Practical", 53, 635, 318),
                l("28", 389, 618, 422), l("26", 485, 618, 518), l("92.86", 560, 618, 634),
                l("Security and Resilience", 53, 734, 347), l("- CB3322-1", 53, 765, 199),
                l("26", 389, 749, 422), l("23", 485, 749, 518), l("88.46", 560, 749, 634),
                l("Cloud Computing -", 53, 865, 291), l("CS2201-1", 53, 896, 176),
                l("38", 389, 880, 422), l("36", 485, 880, 518), l("94.74", 560, 880, 633),
                l("Financial Management -", 53, 996, 355), l("MG1002-1", 53, 1027, 184),
                l("34", 389, 1011, 422), l("31", 485, 1011, 518), l("91.18", 563, 1011, 630),
                l("Secured Mobile", 53, 1126, 249), l("Application", 53, 1158, 196),
                l("Development -", 53, 1190, 238), l("CB3604-1 - Practical", 53, 1221, 319),
                l("16", 389, 1173, 422), l("16", 485, 1173, 518), l("100.00", 553, 1173, 640),
                l("Indian Knowledge", 53, 1318, 277),
                l("Info", 57, 1402, 106),
                l("TCC - Total Classes Conducted", 57, 1445, 413),
                l("TCA - Total Classes Attended", 57, 1478, 389),
                l("Red values indicate attendance below 75%", 57, 1511, 542)));
    }

    @Test
    public void readsProfileAndEverySubjectWithACode() {
        Result r = UniclareScreenshotParser.parse(firstScreen(), 738);

        assertEquals("NNM00XX000", r.usn);
        assertEquals("Test Student", r.name);
        assertEquals(5, r.rows.size());                       // the cut-off "Indian Knowledge" card is skipped

        assertRow(r.rows.get(0), "Cyber Forensics", "CB3002-1", true, 28, 26);
        assertRow(r.rows.get(1), "Security and Resilience", "CB3322-1", false, 26, 23);
        assertRow(r.rows.get(2), "Cloud Computing", "CS2201-1", false, 38, 36);
        assertRow(r.rows.get(3), "Financial Management", "MG1002-1", false, 34, 31);
        assertRow(r.rows.get(4), "Secured Mobile Application Development", "CB3604-1", true, 16, 16);
        for (Row row : r.rows) assertTrue(row.name + " should be confident", row.confident);
    }

    @Test
    public void numbersMergedIntoOneLineStillWork() {
        List<Line> lines = new ArrayList<>(Arrays.asList(
                l("Cloud Computing -", 53, 865, 291), l("CS2201-1", 53, 896, 176),
                l("38 36 94.74", 389, 880, 633)));
        Result r = UniclareScreenshotParser.parse(lines, 738);
        assertEquals(1, r.rows.size());
        assertRow(r.rows.get(0), "Cloud Computing", "CS2201-1", false, 38, 36);
        assertTrue(r.rows.get(0).confident);
    }

    @Test
    public void misreadDigitIsFlaggedNotTrusted() {
        List<Line> lines = new ArrayList<>(Arrays.asList(
                l("Cloud Computing -", 53, 865, 291), l("CS2201-1", 53, 896, 176),
                l("38", 389, 880, 422), l("30", 485, 880, 518), l("94.74", 560, 880, 633)));   // 36 read as 30
        Row row = UniclareScreenshotParser.parse(lines, 738).rows.get(0);
        assertEquals(30, row.attended);
        assertFalse(row.confident);
    }

    @Test
    public void scrolledScreenshotsAreMergedWithoutDuplicates() {
        // Second screenshot, scrolled down: no name card or header, the top bar above the list.
        List<Line> second = new ArrayList<>(Arrays.asList(
                l("Attendance", 95, 136, 263), l("Attendance Summary", 319, 136, 657),
                l("Secured Mobile", 53, 300, 249), l("Application", 53, 332, 196),
                l("Development -", 53, 364, 238), l("CB3604-1 - Practical", 53, 395, 319),
                l("16", 389, 347, 422), l("16", 485, 347, 518), l("100.00", 553, 347, 640),
                l("Indian Knowledge", 53, 492, 277), l("Systems - HU1001-1", 53, 523, 300),
                l("12", 389, 507, 422), l("8", 485, 507, 505), l("66.67", 560, 507, 633)));
        Result merged = UniclareScreenshotParser.merge(Arrays.asList(
                UniclareScreenshotParser.parse(firstScreen(), 738),
                UniclareScreenshotParser.parse(second, 738)));

        assertEquals("NNM00XX000", merged.usn);
        assertEquals(6, merged.rows.size());
        assertRow(merged.rows.get(5), "Indian Knowledge Systems", "HU1001-1", false, 12, 8);
    }

    @Test
    public void usnLookAlikesAreFixedByPosition() {
        assertEquals("NNM23CB012", UniclareScreenshotParser.fixUsn("NNM23CBO12"));   // seen on a real screenshot
        assertEquals("NNM23CB012", UniclareScreenshotParser.fixUsn("nnm23C8OI2"));
        assertEquals("NNM23CB012", UniclareScreenshotParser.fixUsn("NNM23CB012"));
        assertEquals("ABC123", UniclareScreenshotParser.fixUsn("abc123"));          // other formats untouched
    }

    @Test
    public void unrelatedImageGivesNothing() {
        Result r = UniclareScreenshotParser.parse(Arrays.asList(
                l("Holiday photos", 50, 100, 300), l("2026", 500, 100, 560)), 738);
        assertNull(r.usn);
        assertTrue(r.rows.isEmpty());
    }

    private static void assertRow(Row row, String name, String code, boolean practical, int held, int attended) {
        assertEquals(name, row.name);
        assertEquals(code, row.code);
        assertEquals(practical, row.practical);
        assertEquals(held, row.conducted);
        assertEquals(attended, row.attended);
    }
}
