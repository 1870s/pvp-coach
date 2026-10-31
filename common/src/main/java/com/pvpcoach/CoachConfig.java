package com.pvpcoach;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** User settings, stored as config/pvpcoach/config.json. */
public final class CoachConfig {
    public static final int[] TIMEOUT_CHOICES = {5, 8, 10, 15, 20};
    public static final int[] MIN_EXCHANGE_CHOICES = {2, 4, 6, 10};

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static CoachConfig instance = new CoachConfig();

    /** Post a short summary in chat when a fight ends. */
    public boolean chatSummary = true;
    /** Also analyse fights against mobs, for practicing in singleplayer. */
    public boolean trackMobs = false;
    /** A fight ends after this many seconds without any hit between you and your opponent. */
    public int fightTimeoutSeconds = 8;
    /** Fights with fewer hits (landed + taken) are not saved, so a single stray hit is no "fight". */
    public int minExchanges = 4;

    public static CoachConfig get() {
        return instance;
    }

    private static Path file() {
        return PvpCoach.dataDir().resolve("config.json");
    }

    public static void load() {
        Path file = file();
        if (!Files.exists(file)) return;
        try {
            CoachConfig loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), CoachConfig.class);
            if (loaded != null) instance = loaded;
        } catch (Exception e) {
            PvpCoach.LOGGER.warn("Could not read {}, using defaults", file, e);
        }
    }

    public static void save() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(instance), StandardCharsets.UTF_8);
        } catch (IOException e) {
            PvpCoach.LOGGER.warn("Could not save {}", file, e);
        }
    }

    /** Returns the choice after {@code current}, wrapping around. */
    public static int next(int[] choices, int current) {
        for (int i = 0; i < choices.length; i++) {
            if (choices[i] == current) return choices[(i + 1) % choices.length];
        }
        return choices[0];
    }
}
