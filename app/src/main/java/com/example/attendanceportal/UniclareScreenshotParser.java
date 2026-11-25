package com.example.attendanceportal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the text found in a screenshot of Uniclare's "Attendance Summary" page into subject rows.
 *
 * The page is a list of cards. On the left of each card is the subject, wrapped over one to four
 * lines and ending in a course code ("Cloud Computing - CS2201-1", optionally "- Practical"). On the
 * right are three numbers: TCC (classes conducted), TCA (classes attended) and the percentage.
 *
 * Text recognition gives back lines with their positions, not a table, so rows are rebuilt by
 * position: left-hand lines are grouped into subjects, and each subject takes the numbers that sit
 * at the same height. No Android types here so it can be unit tested.
 */
public final class UniclareScreenshotParser {
    private UniclareScreenshotParser() { }

    /** One line of recognised text and its box, in image pixels. */
    public static final class Line {
        final String text;
        final int left, top, right, bottom;

        public Line(String text, int left, int top, int right, int bottom) {
            this.text = text == null ? "" : text.trim();
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        int centerX() { return (left + right) / 2; }
        int centerY() { return (top + bottom) / 2; }
        int height() { return bottom - top; }
    }

    public static final class Row {
        public String name, code;
        public boolean practical;
        public int conducted, attended;
        /** False when the numbers did not add up, so the student should check them. */
        public boolean confident;

        String key() { return code.toUpperCase(Locale.ROOT) + (practical ? "|P" : "|T"); }
    }

    public static final class Result {
        public String usn, name;
        public final List<Row> rows = new ArrayList<>();
    }

    // CB3002-1, CS2201-1, MG1002-1. Allow OCR to drop the dash before the section number.
    private static final Pattern CODE = Pattern.compile("\\b([A-Z]{2,4}\\d{3,5})\\s*-?\\s*(\\d)?\\b");
    private static final Pattern NUMBER = Pattern.compile("\\d{1,4}(?:[.,]\\d{1,2})?");
    private static final Pattern USN = Pattern.compile("Reg\\.?\\s*No\\.?\\s*[:.]?\\s*([A-Za-z0-9]{6,15})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PRACTICAL = Pattern.compile("\\bpractical\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ONLY_PRACTICAL = Pattern.compile("^[-–\\s]*practical$", Pattern.CASE_INSENSITIVE);

    /** Parses one screenshot. {@code imageWidth} decides what counts as the left column. */
    public static Result parse(List<Line> input, int imageWidth) {
        Result result = new Result();
        List<Line> lines = new ArrayList<>();
        for (Line l : input) if (!l.text.isEmpty()) lines.add(l);
        Collections.sort(lines, (a, b) -> a.top != b.top ? Integer.compare(a.top, b.top) : Integer.compare(a.left, b.left));

        int tableTop = Integer.MIN_VALUE;
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            Matcher m = USN.matcher(l.text);
            if (m.find()) {
                result.usn = fixUsn(m.group(1));
                result.name = nameAbove(lines, i);
            }
            String t = l.text.toUpperCase(Locale.ROOT);
            if (t.equals("TCC") || t.equals("TCA") || t.startsWith("SUBJECT") || t.contains("TCC TCA")) {
                tableTop = Math.max(tableTop, l.bottom);
            }
        }

        // Left column: subject text. Right side: the numbers.
        int split = Math.round(imageWidth * 0.48f);
        List<Line> left = new ArrayList<>();
        List<Line> right = new ArrayList<>();
        for (Line l : lines) {
            if (l.top < tableTop) continue;
            if (l.centerX() < split && !isNumeric(l.text)) left.add(l);
            else if (l.left >= split * 0.9 && NUMBER.matcher(l.text).find()) right.add(l);
        }
        if (left.isEmpty()) return result;

        int lineHeight = medianHeight(left);
        List<List<Line>> blocks = new ArrayList<>();
        List<Line> current = new ArrayList<>();
        Line previous = null;
        for (Line l : left) {
            boolean gap = previous != null && l.top - previous.bottom > lineHeight * 0.9;
            if (gap && !current.isEmpty()) {
                blocks.add(current);
                current = new ArrayList<>();
            }
            current.add(l);
            previous = l;
        }
        if (!current.isEmpty()) blocks.add(current);

        for (List<Line> block : blocks) {
            Row row = toRow(block, right, lineHeight);
            if (row != null) result.rows.add(row);
        }
        return result;
    }

    /** Combines several screenshots of the same page (scrolled), keeping the first copy of each subject. */
    public static Result merge(List<Result> parts) {
        Result merged = new Result();
        Map<String, Row> byKey = new LinkedHashMap<>();
        for (Result r : parts) {
            if (merged.usn == null) merged.usn = r.usn;
            if (merged.name == null) merged.name = r.name;
            for (Row row : r.rows) {
                Row seen = byKey.get(row.key());
                if (seen == null || (!seen.confident && row.confident)) byKey.put(row.key(), row);
            }
        }
        merged.rows.addAll(byKey.values());
        return merged;
    }

    private static Row toRow(List<Line> block, List<Line> right, int lineHeight) {
        StringBuilder text = new StringBuilder();
        for (Line l : block) text.append(' ').append(l.text);
        String all = text.toString().trim();

        Matcher m = CODE.matcher(all);
        String codeText = null;
        int codeStart = -1;
        while (m.find()) {                                     // the last code in the block is the subject's
            codeText = m.group(1) + (m.group(2) != null ? "-" + m.group(2) : "");
            codeStart = m.start();
        }
        if (codeText == null) return null;                     // a card cut off before its code, or other text

        Row row = new Row();
        row.code = codeText;
        row.practical = PRACTICAL.matcher(all.substring(codeStart)).find();
        row.name = cleanName(all.substring(0, codeStart));
        if (row.name.isEmpty()) return null;

        // Numbers at the same height as this subject's text
        int top = block.get(0).top - lineHeight / 2;
        int bottom = block.get(block.size() - 1).bottom + lineHeight / 2;
        List<Line> mine = new ArrayList<>();
        for (Line r : right) if (r.centerY() >= top && r.centerY() <= bottom) mine.add(r);
        Collections.sort(mine, (a, b) -> Integer.compare(a.left, b.left));
        List<String> tokens = new ArrayList<>();
        for (Line r : mine) {
            Matcher n = NUMBER.matcher(r.text);
            while (n.find()) tokens.add(n.group().replace(',', '.'));
        }

        List<Integer> ints = new ArrayList<>();
        Double pct = null;
        for (String t : tokens) {
            if (t.contains(".")) {
                if (pct == null) pct = Double.parseDouble(t);
            } else if (ints.size() < 2) {
                ints.add(Integer.parseInt(t));
            } else if (pct == null) {
                pct = Double.parseDouble(t);                       // "100" with the decimals lost
            }
        }
        if (ints.size() < 2) return null;
        row.conducted = ints.get(0);
        row.attended = ints.get(1);
        row.confident = row.conducted > 0 && row.attended <= row.conducted
                && (pct == null || Math.abs(100.0 * row.attended / row.conducted - pct) < 0.1);
        return row;
    }

    /**
     * USNs look like NNM23CB012: letters, 2 digits, 2 letters, 3 digits. Text recognition mixes up
     * look-alikes (O and 0, I and 1, S and 5, B and 8), so fix each character by its position.
     */
    static String fixUsn(String raw) {
        String s = raw.toUpperCase(Locale.ROOT);
        if (s.length() != 10) return s;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean digit = (i >= 3 && i <= 4) || i >= 7;
            out.append(digit ? toDigit(c) : toLetter(c));
        }
        return out.toString();
    }

    private static char toDigit(char c) {
        switch (c) {
            case 'O': case 'D': case 'Q': return '0';
            case 'I': case 'L': return '1';
            case 'Z': return '2';
            case 'S': return '5';
            case 'B': return '8';
            default: return c;
        }
    }

    private static char toLetter(char c) {
        switch (c) {
            case '0': return 'O';
            case '1': return 'I';
            case '5': return 'S';
            case '8': return 'B';
            default: return c;
        }
    }

    /** "Security and Resilience -" to "Security and Resilience". */
    static String cleanName(String raw) {
        String s = raw.replaceAll("\\s+", " ").trim();
        s = s.replaceAll("[\\s\\-–]+$", "").trim();
        return ONLY_PRACTICAL.matcher(s).matches() ? "" : s;
    }

    /** The student's name is the line just above "Reg. No". */
    private static String nameAbove(List<Line> lines, int regIndex) {
        Line reg = lines.get(regIndex);
        Line best = null;
        for (Line l : lines) {
            if (l.bottom > reg.top + reg.height() / 2 || reg.top - l.bottom > reg.height() * 3) continue;
            if (!l.text.matches("[A-Za-z .'-]{2,80}")) continue;
            if (best == null || l.bottom > best.bottom) best = l;
        }
        return best == null ? null : titleCase(best.text);
    }

    private static String titleCase(String s) {
        StringBuilder out = new StringBuilder();
        for (String part : s.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private static boolean isNumeric(String s) {
        return s.matches("[\\d\\s.,%]+");
    }

    private static int medianHeight(List<Line> lines) {
        int[] h = new int[lines.size()];
        for (int i = 0; i < h.length; i++) h[i] = Math.max(1, lines.get(i).height());
        Arrays.sort(h);
        return h[h.length / 2];
    }
}
