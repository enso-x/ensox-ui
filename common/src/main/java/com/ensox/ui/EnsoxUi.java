package com.ensox.ui;

import net.minecraft.resources.Identifier;

/**
 * Library constants. Ensox UI is a client-side library mod ({@value #MOD_ID}) that mods nest
 * jar-in-jar; the loader keeps one copy, the newest. Within one major version the API only grows
 * (see README.md), so a mod built against 1.x works with any later 1.y.
 */
public final class EnsoxUi {
    public static final String MOD_ID = "ensox_ui";

    private EnsoxUi() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
