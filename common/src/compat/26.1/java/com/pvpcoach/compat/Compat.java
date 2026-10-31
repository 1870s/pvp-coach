package com.pvpcoach.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** The few calls that differ between Minecraft versions. This is the 26.1.x variant: the open screen lives on Minecraft. */
public final class Compat {
    private Compat() {}

    public static Screen screen(Minecraft mc) {
        return mc.screen;
    }

    public static void setScreen(Minecraft mc, Screen screen) {
        mc.setScreen(screen);
    }
}
