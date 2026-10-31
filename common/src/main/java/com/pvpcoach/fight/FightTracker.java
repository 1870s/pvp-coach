package com.pvpcoach.fight;

import com.pvpcoach.CoachConfig;
import com.pvpcoach.PvpCoach;
import com.pvpcoach.coach.Coach;
import com.pvpcoach.coach.CoachReport;
import com.pvpcoach.coach.Tip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Watches the local player's fights and fills a {@link FightRecord}.
 *
 * <p>Hits are taken from the server's damage events, which name both the damaged entity and the attacker, so
 * "landed" and "taken" are what the server confirmed, not what the client guessed. How a hit was made (charge,
 * sprint, crit, distance) comes from a snapshot taken when the attack was sent.
 */
public final class FightTracker {
    /** Vanilla treats an attack as fully charged from 90 % on (crits, sprint knockback, sweeps). */
    public static final float FULL_CHARGE = 0.9f;
    /** A damage event this many ticks after an attack still belongs to that attack (covers ping). */
    private static final int SNAPSHOT_MAX_AGE = 20;
    private static final double MACE_SMASH_FALL = 1.5;
    /**
     * Health drops only count as damage for a few ticks after a damage event, so absorption running out or a
     * health update without a hit is not counted.
     */
    private static final int HURT_WINDOW = 3;

    private record Snapshot(float charge, boolean sprint, boolean crit, boolean maceSmash, float reach) {}

    private static FightRecord current;
    private static int opponentId = -1;
    private static int idleTicks;
    private static int combo;
    private static int comboTaken;
    private static float lastHealth;
    private static float lastOpponentHealth;
    private static int myHurtWindow;
    private static int opponentHurtWindow;
    /** Ticks since your last totem pop while you do not hold a new one yet, -1 when not waiting. */
    private static int retotemTimer = -1;
    private static boolean hitWhileEatingCounted;
    private static boolean shieldWasOnCooldown;
    private static Snapshot lastAttack;
    private static int lastAttackAge;

    private FightTracker() {}

    public static boolean inFight() {
        return current != null;
    }

    // ---- Hooks, called from the mixins ----

    /** A melee attack on an entity was sent to the server. */
    public static void onAttack(Player player, Entity target) {
        Minecraft mc = Minecraft.getInstance();
        if (player != mc.player) return;
        if (current == null) {
            if (!isValidOpponent(mc.player, target)) return;
            start(mc.player, target);
        }
        if (target.getId() != opponentId) return;
        Snapshot snapshot = snapshot(mc.player, target);
        current.swings++;
        current.attacks++;
        if (snapshot.charge < FULL_CHARGE) current.weakAttacks++;
        lastAttack = snapshot;
        lastAttackAge = 0;
        idleTicks = 0;
    }

    /** A spear jab. The server decides what it hits, so it only counts towards charge timing. */
    public static void onPiercingAttack() {
        Minecraft mc = Minecraft.getInstance();
        if (current == null || mc.player == null || mc.level == null) return;
        Entity opponent = mc.level.getEntity(opponentId);
        if (opponent == null) return;
        Snapshot snapshot = snapshot(mc.player, opponent);
        current.attacks++;
        if (snapshot.charge < FULL_CHARGE) current.weakAttacks++;
        lastAttack = snapshot;
        lastAttackAge = 0;
    }

    /** A left click that swung at nothing. */
    public static void onMissSwing() {
        Minecraft mc = Minecraft.getInstance();
        if (current == null || mc.player == null) return;
        if (mc.player.getMainHandItem().has(DataComponents.PIERCING_WEAPON)) return;
        current.swings++;
        current.misses++;
    }

    /** Right click with an item, before the game handles it. */
    public static void onUseItem(Player player, InteractionHand hand) {
        if (current == null || player != Minecraft.getInstance().player) return;
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.ENDER_PEARL)) {
            if (!player.getCooldowns().isOnCooldown(stack)) current.pearls++;
        } else if (stack.has(DataComponents.FOOD) && !player.isUsingItem()) {
            current.eats++;
            hitWhileEatingCounted = false;
        }
    }

    /** The server reports that an entity took damage; the ids are -1 when there is no such entity. */
    public static void onDamageEvent(int entityId, int causeId, int directId) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (me == null || mc.level == null) return;

        if (entityId == me.getId()) {
            myHurtWindow = HURT_WINDOW;
            if (current == null) {
                Entity cause = causeId < 0 ? null : mc.level.getEntity(causeId);
                if (cause == null || !isValidOpponent(me, cause)) return;
                start(me, cause);
            }
            if (causeId != opponentId) return;
            current.hitsTaken++;
            comboTaken++;
            combo = 0;
            current.longestComboTaken = Math.max(current.longestComboTaken, comboTaken);
            if (me.isUsingItem() && me.getUseItem().has(DataComponents.FOOD) && !hitWhileEatingCounted) {
                current.eatsInterrupted++;
                hitWhileEatingCounted = true;
            }
            idleTicks = 0;
        } else if (causeId == me.getId()) {
            if (current == null) {
                Entity target = mc.level.getEntity(entityId);
                if (target == null || !isValidOpponent(me, target)) return;
                start(me, target);
            }
            if (entityId != opponentId) return;
            opponentHurtWindow = HURT_WINDOW;
            current.hitsLanded++;
            combo++;
            comboTaken = 0;
            current.longestCombo = Math.max(current.longestCombo, combo);
            if (directId != causeId) {
                current.projectileHits++;
            } else if (lastAttack != null && lastAttackAge <= SNAPSHOT_MAX_AGE) {
                current.meleeHits++;
                if (lastAttack.sprint) current.sprintHits++;
                if (lastAttack.crit) current.critHits++;
                if (lastAttack.maceSmash) current.maceSmashes++;
                current.reachSum += lastAttack.reach;
                current.reachSamples++;
                lastAttack = null;
            }
            idleTicks = 0;
        }
    }

    /** Entity status events: totem pops and deaths. */
    public static void onEntityEvent(Entity entity, byte eventId) {
        Minecraft mc = Minecraft.getInstance();
        if (current == null || entity == null || mc.player == null) return;
        boolean isMe = entity == mc.player;
        boolean isOpponent = entity.getId() == opponentId;
        if (eventId == EntityEvent.PROTECTED_FROM_DEATH) {
            if (isMe) {
                current.totemPops++;
                retotemTimer = 0;
            } else if (isOpponent) {
                current.opponentPops++;
            }
        } else if (eventId == EntityEvent.DEATH && isOpponent) {
            finish(FightRecord.Result.WIN);
        }
    }

    // ---- Per tick ----

    public static void tick(Minecraft mc) {
        if (current == null) return;
        LocalPlayer me = mc.player;
        if (me == null || mc.level == null) {
            finish(FightRecord.Result.ENDED);
            return;
        }
        current.durationTicks++;
        idleTicks++;
        if (lastAttack != null) lastAttackAge++;

        float health = me.getHealth() + me.getAbsorptionAmount();
        if (health < lastHealth && myHurtWindow > 0) current.damageTaken += lastHealth - health;
        lastHealth = health;
        if (myHurtWindow > 0) myHurtWindow--;
        current.lowestHealth = Math.min(current.lowestHealth, me.getHealth());

        if (!me.isUsingItem()) hitWhileEatingCounted = false;
        if (me.isBlocking()) current.blockingTicks++;
        ItemStack offhand = me.getOffhandItem();
        if (offhand.is(Items.SHIELD)) {
            current.hadShield = true;
            boolean onCooldown = me.getCooldowns().isOnCooldown(offhand);
            if (onCooldown && !shieldWasOnCooldown) current.shieldsDisabled++;
            shieldWasOnCooldown = onCooldown;
        }

        if (retotemTimer >= 0) {
            if (me.getOffhandItem().is(Items.TOTEM_OF_UNDYING) || me.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
                current.retotemTicks.add(retotemTimer);
                retotemTimer = -1;
            } else {
                retotemTimer++;
            }
        }

        if (me.isDeadOrDying()) {
            finish(FightRecord.Result.LOSS);
            return;
        }
        if (mc.level.getEntity(opponentId) instanceof LivingEntity opponent) {
            float opponentHealth = opponent.getHealth() + opponent.getAbsorptionAmount();
            if (opponentHealth < lastOpponentHealth && opponentHurtWindow > 0) {
                current.damageDealt += lastOpponentHealth - opponentHealth;
            }
            lastOpponentHealth = opponentHealth;
            if (opponentHurtWindow > 0) opponentHurtWindow--;
            if (opponent.isDeadOrDying()) {
                finish(FightRecord.Result.WIN);
                return;
            }
        }
        if (idleTicks > CoachConfig.get().fightTimeoutSeconds * 20) {
            // The idle wait at the end is not part of the fight.
            current.durationTicks = Math.max(0, current.durationTicks - idleTicks);
            finish(FightRecord.Result.ENDED);
        }
    }

    // ---- Internals ----

    private static boolean isValidOpponent(LocalPlayer me, Entity entity) {
        if (entity == me || !entity.isAlive()) return false;
        if (entity instanceof Player player) return !player.isSpectator();
        return CoachConfig.get().trackMobs && entity instanceof Mob;
    }

    private static void start(LocalPlayer me, Entity opponent) {
        current = new FightRecord();
        current.timestamp = System.currentTimeMillis();
        current.opponent = opponent.getName().getString();
        current.opponentIsPlayer = opponent instanceof Player;
        current.maxHealth = me.getMaxHealth();
        opponentId = opponent.getId();
        idleTicks = 0;
        combo = 0;
        comboTaken = 0;
        retotemTimer = -1;
        lastAttack = null;
        hitWhileEatingCounted = false;
        shieldWasOnCooldown = false;
        // The hit that started the fight already happened, so its health drop shows up in the next tick.
        myHurtWindow = HURT_WINDOW;
        opponentHurtWindow = HURT_WINDOW;
        lastHealth = me.getHealth() + me.getAbsorptionAmount();
        lastOpponentHealth = opponent instanceof LivingEntity living ? living.getHealth() + living.getAbsorptionAmount() : 0;
    }

    private static Snapshot snapshot(LocalPlayer me, Entity target) {
        float charge = me.getAttackStrengthScale(0.5f);
        boolean charged = charge >= FULL_CHARGE;
        boolean sprint = charged && me.isSprinting();
        // Same conditions vanilla checks for a critical hit.
        boolean crit = charged && me.fallDistance > 0 && !me.onGround() && !me.onClimbable() && !me.isInWater()
            && !me.isPassenger() && !me.isSprinting() && !me.hasEffect(MobEffects.BLINDNESS);
        boolean maceSmash = me.getMainHandItem().is(Items.MACE) && me.fallDistance > MACE_SMASH_FALL;
        float reach = (float) Math.sqrt(target.getBoundingBox().distanceToSqr(me.getEyePosition()));
        return new Snapshot(charge, sprint, crit, maceSmash, reach);
    }

    private static void finish(FightRecord.Result result) {
        FightRecord record = current;
        current = null;
        opponentId = -1;
        lastAttack = null;
        if (retotemTimer >= 0) record.missedRetotems++;
        retotemTimer = -1;
        record.result = result;
        if (record.lowestHealth == Float.MAX_VALUE) record.lowestHealth = record.maxHealth;

        if (record.hitsLanded + record.hitsTaken < CoachConfig.get().minExchanges) return;
        CoachReport report = Coach.analyze(record);
        record.score = report.score();
        FightHistory.add(record);
        PvpCoach.LOGGER.info("[PvP Coach] fight vs {} saved: {} score {}", record.opponent, result, record.score);
        if (CoachConfig.get().chatSummary) postSummary(record, report);
    }

    private static void postSummary(FightRecord record, CoachReport report) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        MutableComponent line = Component.literal("[PvP Coach] ").withStyle(ChatFormatting.LIGHT_PURPLE)
            .append(Component.translatable("pvpcoach.result." + record.result.name().toLowerCase())
                .withStyle(resultColor(record.result), ChatFormatting.BOLD))
            .append(Component.translatable("pvpcoach.chat.versus", record.opponent).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.translatable("pvpcoach.chat.score", record.score, report.grade()).withStyle(ChatFormatting.WHITE));
        Tip top = report.topTip();
        if (top != null) {
            line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                .append(top.shortText().copy().withStyle(top.kind() == Tip.Kind.GOOD ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        }
        line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.translatable("pvpcoach.chat.open", PvpCoach.OPEN_COACH.getTranslatedKeyMessage())
                .withStyle(ChatFormatting.GRAY));
        mc.player.sendSystemMessage(line);
    }

    public static ChatFormatting resultColor(FightRecord.Result result) {
        return switch (result) {
            case WIN -> ChatFormatting.GREEN;
            case LOSS -> ChatFormatting.RED;
            case ENDED -> ChatFormatting.GRAY;
        };
    }
}
