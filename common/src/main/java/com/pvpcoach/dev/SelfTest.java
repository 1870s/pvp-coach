package com.pvpcoach.dev;

import com.pvpcoach.CoachConfig;
import com.pvpcoach.PvpCoach;
import com.pvpcoach.compat.Compat;
import com.pvpcoach.fight.FightHistory;
import com.pvpcoach.fight.FightRecord;
import com.pvpcoach.screen.CoachScreen;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.FileUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

/**
 * Development-only smoke test (enabled with -Dpvpcoach.selftest=true, excluded from the release jar):
 * creates a survival test world, fights three husks in different styles (clean, sloppy, abandoned) through the
 * real attack key, then screenshots the chat summary and every tab of the coach screen, and quits.
 */
public final class SelfTest {
    private record Step(String name, Runnable action, int waitAfter) {}

    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static boolean worldRequested;
    private static boolean scripted;
    private static int wait;

    private SelfTest() {}

    public static void install() {
        PvpCoach.LOGGER.info("[SelfTest] enabled");
        PvpCoach.addTickListener(SelfTest::tick);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (!worldRequested) {
                if (!(Compat.screen(mc) instanceof TitleScreen)) return;
                // The test clicks through the real attack key, which a pause menu or tutorial toast would get in the way of.
                mc.options.pauseOnLostFocus = false;
                mc.options.tutorialStep = TutorialSteps.NONE;
                mc.getTutorial().setStep(TutorialSteps.NONE);
                mc.options.guiScale().set(2);
                mc.resizeGui();
                createTestWorld(mc);
                worldRequested = true;
                return;
            }
            if (!scripted) {
                if (mc.player == null || mc.level == null || Compat.screen(mc) != null) return;
                CoachConfig.get().trackMobs = true;
                CoachConfig.get().fightTimeoutSeconds = 5;
                FightHistory.clear();
                script(mc);
                scripted = true;
                wait = 60;
                return;
            }
            if (Compat.screen(mc) instanceof PauseScreen) Compat.setScreen(mc, null);
            if (wait > 0) {
                wait--;
                return;
            }
            Step step = STEPS.poll();
            if (step == null) return;
            step.action.run();
            wait = step.waitAfter;
        } catch (Exception e) {
            PvpCoach.LOGGER.error("[SelfTest] failed", e);
            STEPS.clear();
            mc.stop();
        }
    }

    private static void createTestWorld(Minecraft mc) throws Exception {
        String folder = FileUtil.findAvailableName(mc.getLevelSource().getBaseDir(), "CoachTest", "");
        LevelSettings settings = new LevelSettings("CoachTest", GameType.SURVIVAL, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(folder, settings, new WorldOptions(7L, false, false),
            SelfTest::flatWorld, Compat.screen(mc));
    }

    /** The superflat preset, looked up from the registry so it works on every 26.x version. */
    private static WorldDimensions flatWorld(HolderLookup.Provider registries) {
        return registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions();
    }

    private static void script(Minecraft mc) {
        step("setup", () -> {
            command("time set noon");
            command("weather clear");
            // Flat worlds spawn slimes, which can kill the player in the middle of a scripted fight.
            command("gamerule spawn_mobs false");
            command("kill @e[type=!player]");
            command("item replace entity @p weapon.mainhand with diamond_sword");
            command("item replace entity @p weapon.offhand with totem_of_undying");
        }, 20);

        // Fight 1: clean. Every hit fully charged, mostly sprinting, takes few hits, wins.
        spawnHusk();
        for (int i = 0; i < 8; i++) {
            final boolean sprint = i % 4 != 3;
            step("aim", () -> aimAtHusk(mc), 2);
            step("hit", () -> {
                mc.player.setSprinting(sprint);
                attack(mc);
            }, 14);
            if (i == 3) step("take hit", () -> damagePlayer(3), 10);
        }
        step("kill", () -> command("kill @e[type=husk]"), 40);

        // Fight 2: sloppy. Spam clicks, misses, gets comboed, pops a totem and refills late; still wins.
        spawnHusk();
        for (int i = 0; i < 12; i++) {
            if (i % 3 == 2) {
                step("look away", () -> mc.player.setXRot(-80), 2);
            } else {
                step("aim", () -> aimAtHusk(mc), 2);
            }
            step("spam", () -> {
                mc.player.setSprinting(false);
                attack(mc);
            }, 4);
        }
        for (int i = 0; i < 6; i++) step("take hit", () -> damagePlayer(2), 11);
        step("pop totem", () -> damagePlayer(60), 30);
        step("refill totem", () -> command("item replace entity @p weapon.offhand with totem_of_undying"), 10);
        step("aim", () -> aimAtHusk(mc), 2);
        step("hit", () -> attack(mc), 14);
        step("kill", () -> command("kill @e[type=husk]"), 40);

        // Fight 3: a few hits, then walk away: ends by timeout.
        step("heal", () -> command("effect give @p instant_health 1 5"), 10);
        spawnHusk();
        for (int i = 0; i < 4; i++) {
            step("aim", () -> aimAtHusk(mc), 2);
            step("hit", () -> attack(mc), 13);
        }
        step("take hit", () -> damagePlayer(2), 8 * 20);

        // Screens.
        step("log", SelfTest::logHistory, 5);
        step("shot chat", () -> shot(mc, "coach_chat"), 10);
        step("open report", () -> Compat.setScreen(mc, new CoachScreen(CoachScreen.Tab.REPORT)), 20);
        step("shot report", () -> shot(mc, "coach_report"), 10);
        step("scroll report", () -> Compat.screen(mc).mouseScrolled(0, 0, 0, -20), 10);
        step("shot report tips", () -> shot(mc, "coach_report_tips"), 10);
        step("open older report", () -> Compat.setScreen(mc, new CoachScreen(CoachScreen.Tab.REPORT).showingFight(1)), 20);
        step("shot older report", () -> shot(mc, "coach_report_older"), 10);
        step("open progress", () -> Compat.setScreen(mc, new CoachScreen(CoachScreen.Tab.PROGRESS)), 20);
        step("shot progress", () -> shot(mc, "coach_progress"), 10);
        step("open settings", () -> Compat.setScreen(mc, new CoachScreen(CoachScreen.Tab.SETTINGS)), 20);
        step("shot settings", () -> shot(mc, "coach_settings"), 10);
        step("quit", () -> {
            PvpCoach.LOGGER.info("[SelfTest] finished");
            mc.stop();
        }, 0);
    }

    private static void step(String name, Runnable action, int waitAfter) {
        STEPS.add(new Step(name, action, waitAfter));
    }

    private static void spawnHusk() {
        step("spawn husk", () -> command("execute as @p at @s run summon husk ^ ^ ^2.5 "
            + "{NoAI:1b,PersistenceRequired:1b,attributes:[{id:\"minecraft:max_health\",base:200.0}],Health:200.0f}"), 20);
    }

    private static void damagePlayer(int amount) {
        command("damage @p " + amount + " minecraft:mob_attack by @e[type=husk,limit=1,sort=nearest]");
    }

    private static void attack(Minecraft mc) {
        KeyMapping.click(mc.options.keyAttack.getDefaultKey());
    }

    private static void aimAtHusk(Minecraft mc) {
        Entity husk = null;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity.getType() == DevCompat.husk()) husk = entity;
        }
        if (husk == null) return;
        Vec3 d = husk.getEyePosition().subtract(mc.player.getEyePosition());
        double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
        mc.player.setYRot((float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f);
        mc.player.setXRot((float) (-(Mth.atan2(d.y, horizontal) * Mth.RAD_TO_DEG)));
    }

    private static void command(String command) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    private static void logHistory() {
        for (FightRecord f : FightHistory.all()) {
            PvpCoach.LOGGER.info("[SelfTest] fight vs {}: {} score={} landed={} taken={} swings={} misses={} attacks={} weak={} "
                    + "melee={} sprint={} crits={} combo={}/{} pops={} retotem={} missedRetotem={} reach={} dealt={} taken={} duration={}",
                f.opponent, f.result, f.score, f.hitsLanded, f.hitsTaken, f.swings, f.misses, f.attacks, f.weakAttacks,
                f.meleeHits, f.sprintHits, f.critHits, f.longestCombo, f.longestComboTaken, f.totemPops, f.retotemTicks,
                f.missedRetotems, f.averageReach(), f.damageDealt, f.damageTaken, f.durationTicks);
        }
        PvpCoach.LOGGER.info("[SelfTest] {} fights recorded", FightHistory.all().size());
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", DevCompat.mainRenderTarget(mc), 1,
            msg -> PvpCoach.LOGGER.info("[SelfTest] screenshot {}", name));
    }
}
