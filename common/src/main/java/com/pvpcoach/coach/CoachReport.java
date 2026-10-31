package com.pvpcoach.coach;

import java.util.List;

/** The coach's verdict on one fight: score 0-100, a letter grade and tips sorted FIX, IMPROVE, GOOD. */
public record CoachReport(int score, String grade, List<Tip> tips) {
    /** The tip to show in the chat summary: the most important mistake, or the best strength if there is none. */
    public Tip topTip() {
        return tips.isEmpty() ? null : tips.getFirst();
    }

    public static String grade(int score) {
        if (score >= 90) return "S";
        if (score >= 80) return "A";
        if (score >= 65) return "B";
        if (score >= 50) return "C";
        return "D";
    }
}
