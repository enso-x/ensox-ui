package com.ensox.ui.event;

import com.mojang.blaze3d.platform.InputConstants;

/** Keyboard modifier bit set carried by every input event (InputConstants layout). */
public record Modifiers(int bits) {
    public static final Modifiers NONE = new Modifiers(0);

    public boolean shift() {
        return (bits & InputConstants.MOD_SHIFT) != 0;
    }

    public boolean control() {
        return (bits & InputConstants.MOD_CONTROL) != 0;
    }

    public boolean alt() {
        return (bits & InputConstants.MOD_ALT) != 0;
    }

    public boolean superKey() {
        return (bits & InputConstants.MOD_SUPER) != 0;
    }

    public boolean any() {
        return bits != 0;
    }
}
