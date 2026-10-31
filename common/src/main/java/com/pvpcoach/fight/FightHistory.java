package com.pvpcoach.fight;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.pvpcoach.PvpCoach;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Saved fights, newest first, stored as config/pvpcoach/history.json. */
public final class FightHistory {
    public static final int MAX_FIGHTS = 100;

    private static final Gson GSON = new GsonBuilder().create();
    private static final List<FightRecord> FIGHTS = new ArrayList<>();

    private FightHistory() {}

    /** Newest first. */
    public static List<FightRecord> all() {
        return Collections.unmodifiableList(FIGHTS);
    }

    public static void add(FightRecord record) {
        FIGHTS.addFirst(record);
        while (FIGHTS.size() > MAX_FIGHTS) FIGHTS.removeLast();
        save();
    }

    public static void clear() {
        FIGHTS.clear();
        save();
    }

    private static Path file() {
        return PvpCoach.dataDir().resolve("history.json");
    }

    public static void load() {
        Path file = file();
        if (!Files.exists(file)) return;
        try {
            List<FightRecord> loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                new TypeToken<List<FightRecord>>() {}.getType());
            FIGHTS.clear();
            if (loaded != null) FIGHTS.addAll(loaded);
        } catch (Exception e) {
            PvpCoach.LOGGER.warn("Could not read {}, starting with an empty history", file, e);
        }
    }

    private static void save() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(FIGHTS), StandardCharsets.UTF_8);
        } catch (IOException e) {
            PvpCoach.LOGGER.warn("Could not save {}", file, e);
        }
    }
}
