package com.pvpcoach.coach;

import com.pvpcoach.fight.FightRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Turns a {@link FightRecord} into a score and tips. Every rule only fires when the fight gave it enough data,
 * so a short fight does not get judged on two swings.
 */
public final class Coach {
    private Coach() {}

    public static CoachReport analyze(FightRecord f) {
        List<Tip> tips = new ArrayList<>();
        double weakRatio = 1 - f.chargedRatio();
        double retotem = f.averageRetotemSeconds();

        // ---- Mistakes ----
        if (f.attacks >= 5 && weakRatio > 0.25) {
            tips.add(new Tip(weakRatio > 0.45 ? Tip.Kind.FIX : Tip.Kind.IMPROVE, "weak_hits", percent(weakRatio)));
        }
        if (f.swings >= 8 && f.accuracy() < 0.6) {
            tips.add(new Tip(f.accuracy() < 0.4 ? Tip.Kind.FIX : Tip.Kind.IMPROVE, "accuracy", f.misses, f.swings));
        }
        if (f.meleeHits >= 5 && f.sprintRatio() < 0.35) {
            tips.add(new Tip(Tip.Kind.IMPROVE, "sprint", percent(f.sprintRatio())));
        }
        if (f.hitsTaken >= 5 && f.tradeRatio() < 0.75) {
            tips.add(new Tip(f.tradeRatio() < 0.5 ? Tip.Kind.FIX : Tip.Kind.IMPROVE, "trading", f.hitsTaken, f.hitsLanded));
        }
        if (f.longestComboTaken >= 4) {
            tips.add(new Tip(f.longestComboTaken >= 6 ? Tip.Kind.FIX : Tip.Kind.IMPROVE, "combo_taken", f.longestComboTaken));
        }
        if (f.missedRetotems > 0) {
            tips.add(new Tip(Tip.Kind.FIX, "retotem_missed", f.missedRetotems));
        }
        if (!f.retotemTicks.isEmpty() && retotem > 1.0) {
            tips.add(new Tip(retotem > 2.0 ? Tip.Kind.FIX : Tip.Kind.IMPROVE, "retotem_slow", seconds(retotem)));
        }
        if (f.eatsInterrupted >= 2) {
            tips.add(new Tip(Tip.Kind.IMPROVE, "eat_interrupted", f.eatsInterrupted));
        }
        if (f.hadShield && f.blockingTicks == 0 && f.hitsTaken >= 6) {
            tips.add(new Tip(Tip.Kind.IMPROVE, "shield_unused"));
        }
        if (f.reachSamples >= 5 && f.averageReach() < 2.0) {
            tips.add(new Tip(Tip.Kind.IMPROVE, "close_range", decimal(f.averageReach())));
        }
        if (f.meleeHits >= 8 && f.critHits == 0 && f.sprintRatio() >= 0.35) {
            tips.add(new Tip(Tip.Kind.IMPROVE, "no_crits"));
        }

        // ---- Strengths ----
        if (f.attacks >= 5 && weakRatio <= 0.1) tips.add(new Tip(Tip.Kind.GOOD, "good_charge", percent(f.chargedRatio())));
        if (f.swings >= 8 && f.accuracy() >= 0.8) tips.add(new Tip(Tip.Kind.GOOD, "good_aim", percent(f.accuracy())));
        if (f.meleeHits >= 5 && f.sprintRatio() >= 0.6) tips.add(new Tip(Tip.Kind.GOOD, "good_sprint", percent(f.sprintRatio())));
        if (f.longestCombo >= 5) tips.add(new Tip(Tip.Kind.GOOD, "good_combo", f.longestCombo));
        if (!f.retotemTicks.isEmpty() && f.missedRetotems == 0 && retotem <= 0.6) {
            tips.add(new Tip(Tip.Kind.GOOD, "good_retotem", seconds(retotem)));
        }
        if (f.result == FightRecord.Result.WIN && f.hitsTaken <= 2) tips.add(new Tip(Tip.Kind.GOOD, "clean_win", f.hitsTaken));

        tips.sort(Comparator.comparing(Tip::kind));
        int score = score(f, weakRatio, retotem);
        return new CoachReport(score, CoachReport.grade(score), List.copyOf(tips));
    }

    private static int score(FightRecord f, double weakRatio, double retotem) {
        double score = 100;
        if (f.attacks >= 3) score -= weakRatio * 35;
        if (f.swings >= 5) score -= (1 - f.accuracy()) * 20;
        if (f.meleeHits >= 3) score -= (1 - Math.min(1, f.sprintRatio() / 0.6)) * 15;
        if (f.hitsTaken > 0) score -= (1 - Math.min(1, f.tradeRatio() / 1.5)) * 20;
        if (!f.retotemTicks.isEmpty() && retotem > 0.6) score -= Math.min(10, (retotem - 0.6) * 8);
        score -= f.missedRetotems * 5;
        score -= Math.min(9, f.eatsInterrupted * 3);
        score -= Math.min(8, Math.max(0, f.longestComboTaken - 3) * 2);
        if (f.result == FightRecord.Result.WIN) score += 5;
        return (int) Math.round(Math.max(0, Math.min(100, score)));
    }

    public static String percent(double ratio) {
        return Integer.toString((int) Math.round(ratio * 100));
    }

    public static String seconds(double seconds) {
        return String.format(Locale.ROOT, "%.1f", seconds);
    }

    public static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
