package com.ensox.ui.event;

import org.lwjgl.glfw.GLFW;

/** Keyboard modifier bit set carried by every input event (GLFW layout). */
public record Modifiers(int bits) {
    public static final Modifiers NONE = new Modifiers(0);

    public boolean shift() {
        return (bits & GLFW.GLFW_MOD_SHIFT) != 0;
    }

    public boolean control() {
        return (bits & GLFW.GLFW_MOD_CONTROL) != 0;
    }

    public boolean alt() {
        return (bits & GLFW.GLFW_MOD_ALT) != 0;
    }

    public boolean superKey() {
        return (bits & GLFW.GLFW_MOD_SUPER) != 0;
    }

    public boolean any() {
        return bits != 0;
    }
}
