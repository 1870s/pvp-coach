package com.pvpcoach.fight;

import java.util.ArrayList;
import java.util.List;

/** Everything PvP Coach measured during one fight. Plain fields so it can be stored with Gson. */
public final class FightRecord {
    public enum Result { WIN, LOSS, ENDED }

    public long timestamp;
    public String opponent = "?";
    public boolean opponentIsPlayer;
    public Result result = Result.ENDED;
    public int durationTicks;

    /** Every left click while fighting: attacks on the opponent plus swings that hit nothing. */
    public int swings;
    public int misses;
    /** Melee attacks aimed at the opponent (sent to the server, whether they did damage or not). */
    public int attacks;
    /** Attacks made before the weapon was charged to 90 %. */
    public int weakAttacks;

    /** Hits the server confirmed, from the opponent's damage events. */
    public int hitsLanded;
    public int hitsTaken;
    /** Of the landed melee hits: hits made while sprinting with a charged weapon (extra knockback). */
    public int sprintHits;
    public int critHits;
    public int maceSmashes;
    /** Landed melee hits that had an attack snapshot, the base for sprint/crit/reach percentages. */
    public int meleeHits;
    public int projectileHits;

    public int longestCombo;
    public int longestComboTaken;

    public float damageDealt;
    public float damageTaken;
    public float lowestHealth = Float.MAX_VALUE;
    public float maxHealth = 20;
    public float reachSum;
    public int reachSamples;

    public int totemPops;
    public int opponentPops;
    /** Ticks it took to hold a totem again after each of your pops. */
    public List<Integer> retotemTicks = new ArrayList<>();
    /** Pops after which you never held a totem again before the fight ended. */
    public int missedRetotems;

    public int pearls;
    public int eats;
    public int eatsInterrupted;
    public boolean hadShield;
    /** Ticks you spent with your shield raised. */
    public int blockingTicks;
    /** Times an axe put your shield on cooldown. */
    public int shieldsDisabled;

    /** Coach score 0-100, stored so the progress page does not have to re-analyse every fight. */
    public int score;

    public double accuracy() {
        return swings == 0 ? 1 : (double) (swings - misses) / swings;
    }

    public double chargedRatio() {
        return attacks == 0 ? 1 : (double) (attacks - weakAttacks) / attacks;
    }

    public double sprintRatio() {
        return meleeHits == 0 ? 0 : (double) sprintHits / meleeHits;
    }

    public double critRatio() {
        return meleeHits == 0 ? 0 : (double) critHits / meleeHits;
    }

    public double averageReach() {
        return reachSamples == 0 ? 0 : reachSum / reachSamples;
    }

    public double averageRetotemSeconds() {
        if (retotemTicks.isEmpty()) return 0;
        double sum = 0;
        for (int ticks : retotemTicks) sum += ticks;
        return sum / retotemTicks.size() / 20.0;
    }

    public double tradeRatio() {
        return hitsTaken == 0 ? hitsLanded : (double) hitsLanded / hitsTaken;
    }
}
