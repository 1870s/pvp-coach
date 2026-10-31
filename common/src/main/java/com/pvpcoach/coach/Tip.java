package com.pvpcoach.coach;

import net.minecraft.network.chat.Component;

/**
 * One piece of feedback. {@code id} names the rule that produced it (lang keys {@code pvpcoach.tip.<id>} and
 * {@code pvpcoach.tip.<id>.short}), so the progress page can count which mistakes come up most.
 */
public record Tip(Kind kind, String id, Object... args) {
    public enum Kind {
        /** Costs you fights; fix this first. */
        FIX,
        /** Worth working on. */
        IMPROVE,
        /** Something you did well. */
        GOOD
    }

    public Component text() {
        return Component.translatable("pvpcoach.tip." + id, args);
    }

    public Component shortText() {
        return Component.translatable("pvpcoach.tip." + id + ".short", args);
    }
}
