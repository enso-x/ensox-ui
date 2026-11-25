package com.ensox.ui.core;

import com.ensox.ui.geom.Rect;
import com.mojang.blaze3d.platform.TextInputManager;
import net.minecraft.client.Minecraft;

/**
 * Text input for widgets that take typed characters ({@link Widget#onText}).
 *
 * <p>Minecraft 26.3 reads the keyboard through SDL, which sends typed characters only while text
 * input is on; vanilla's {@code EditBox} turns it on with focus. Every widget that edits text
 * starts it when editing begins and stops it when editing ends or the widget detaches. The owner
 * is the widget, so stopping never ends another widget's (or a vanilla field's) input. Key
 * presses arrive without it; only characters need it, so shortcuts stay free of IME composition
 * while nothing is being edited.</p>
 */
public final class TextInput {
    private TextInput() {
    }

    /**
     * Turns text input on for {@code owner}.
     *
     * @param area GUI-space rectangle near the edited text; places the IME candidate window
     */
    public static void start(Object owner, Rect area) {
        TextInputManager input = Minecraft.getInstance().textInputManager();
        updateArea(area);
        input.startTextInput(owner);
    }

    /** Reposition the IME candidate area without restarting the active editing session. */
    public static void updateArea(Rect area) {
        Minecraft.getInstance().textInputManager().setTextInputArea(area.x(), area.y(), area.right(), area.bottom());
    }

    /** Turns text input off if {@code owner} turned it on. */
    public static void stop(Object owner) {
        Minecraft.getInstance().textInputManager().stopTextInput(owner);
    }
}
