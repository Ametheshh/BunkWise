package com.example.attendanceportal;

import java.util.Locale;

/**
 * USNs in the data start with NNM for some students and NMM for most. People type either spelling,
 * so login accepts both for the same student. Nothing is stored differently: this only maps what was
 * typed onto the account that exists.
 */
public final class UsnAlias {
    private UsnAlias() { }

    private static boolean hasCollegePrefix(String s) {
        if (s == null || s.length() <= 3) return false;
        String p = s.substring(0, 3);
        return p.equals("nnm") || p.equals("nmm");
    }

    /** "nnm23cb007@nmamit.in" gives "nmm23cb007@nmamit.in" and the other way round. Null if no such prefix. */
    public static String swapPrefix(String lower) {
        if (!hasCollegePrefix(lower)) return null;
        return (lower.startsWith("nnm") ? "nmm" : "nnm") + lower.substring(3);
    }

    /**
     * The initial password is the lowercase USN. Make the typed text use the same first three letters
     * as the real USN, so nnm23cb007 and nmm23cb007 both work for the student whose USN is NMM23CB007.
     */
    public static String initialPasswordCandidate(String typed, String usn) {
        String t = typed.trim().toLowerCase(Locale.ROOT);
        String u = usn.toLowerCase(Locale.ROOT);
        if (hasCollegePrefix(t) && hasCollegePrefix(u)) return u.substring(0, 3) + t.substring(3);
        return t;
    }
}
