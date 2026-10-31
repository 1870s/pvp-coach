package com.pvpcoach;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import com.pvpcoach.compat.Compat;
import com.pvpcoach.fight.FightHistory;
import com.pvpcoach.fight.FightTracker;
import com.pvpcoach.screen.CoachScreen;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

/** Loader independent core of PvP Coach. The NeoForge and Fabric entry points only wire events into this class. */
public final class PvpCoach {
    public static final String MOD_ID = "pvpcoach";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final Identifier KEY_CATEGORY_ID = Identifier.fromNamespaceAndPath(MOD_ID, "main");
    public static final String OPEN_COACH_NAME = "key.pvpcoach.open";
    public static final int OPEN_COACH_DEFAULT_KEY = InputConstants.KEY_K;

    /** Loader specific services. */
    public interface Platform {
        Path configDir();
    }

    public static KeyMapping OPEN_COACH;
    private static Platform platform;
    private static final List<Runnable> TICK_LISTENERS = new ArrayList<>();

    private PvpCoach() {}

    /** Called from the loader entry point with the loader's key category instance. */
    public static void init(Platform loaderPlatform, KeyMapping.Category category) {
        platform = loaderPlatform;
        // The keyboard input type was renamed in 26.3 (KEYSYM -> KEYBOARD); this constructor defaults to it on every version.
        OPEN_COACH = new KeyMapping(OPEN_COACH_NAME, OPEN_COACH_DEFAULT_KEY, category);
        if (Boolean.getBoolean("pvpcoach.selftest")) installSelfTest();
    }

    public static Platform platform() {
        return platform;
    }

    public static Path dataDir() {
        return platform.configDir().resolve(MOD_ID);
    }

    /** Runs once the game is far enough initialized to read the config. */
    public static void onClientSetup() {
        CoachConfig.load();
        FightHistory.load();
    }

    public static void addTickListener(Runnable listener) {
        TICK_LISTENERS.add(listener);
    }

    /** Called at the end of every client tick. */
    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_COACH.consumeClick()) {
            if (Compat.screen(mc) == null) Compat.setScreen(mc, new CoachScreen());
        }
        FightTracker.tick(mc);
        for (Runnable listener : TICK_LISTENERS) listener.run();
    }

    /** Dev-only smoke test; the class is not part of the release jars, so it is loaded reflectively. */
    private static void installSelfTest() {
        try {
            Class.forName("com.pvpcoach.dev.SelfTest").getMethod("install").invoke(null);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Self test requested but not available", e);
        }
    }
}
