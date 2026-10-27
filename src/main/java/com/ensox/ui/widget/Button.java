package com.ensox.ui.widget;

import com.ensox.ui.asset.AssetKey;
import com.ensox.ui.asset.AssetLibrary;
import com.ensox.ui.asset.UiAsset;
import com.ensox.ui.core.Widget;
import com.ensox.ui.event.KeyEvent;
import com.ensox.ui.event.PointerEvent;
import com.ensox.ui.geom.Rect;
import com.ensox.ui.paint.Canvas;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Clickable widget with a pluggable {@link Skin} and optional text or icon.
 *
 * <ul>
 *     <li>Activates on primary-button release inside the button (moving off
 *     cancels), or Enter/Space while focused; plays the vanilla click sound.</li>
 *     <li>{@link #selected(boolean)} is a sticky state for toggles and tabs.</li>
 *     <li>New looks are new {@link Skin}s; new behavior is a subclass.</li>
 * </ul>
 */
public class Button extends Widget {
    /**
     * Everything a skin needs to choose its look. {@code focused} is the
     * visible (keyboard) focus only, so a clicked button does not stay lit.
     */
    public record State(boolean enabled, boolean hovered, boolean focused, boolean pressed, boolean selected) {
        public boolean highlighted() {
            return enabled && (hovered || focused);
        }
    }

    @FunctionalInterface
    public interface Skin {
        void paint(Canvas canvas, Rect bounds, State state);

        /** Text and icon color; vanilla white / grey by default. */
        default int contentColor(State state) {
            return state.enabled() ? 0xFFFFFFFF : 0xFFA0A0A0;
        }
    }

    private static final Identifier VANILLA = Identifier.withDefaultNamespace("widget/button");
    private static final Identifier VANILLA_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/button_highlighted");
    private static final Identifier VANILLA_DISABLED = Identifier.withDefaultNamespace("widget/button_disabled");

    /** The vanilla button look. */
    public static final Skin VANILLA_SKIN = (canvas, bounds, state) -> canvas.sprite(
        !state.enabled() ? VANILLA_DISABLED : state.highlighted() ? VANILLA_HIGHLIGHTED : VANILLA, bounds);

    private static final int TEXT_MARGIN = 8;

    private Skin skin = VANILLA_SKIN;
    private @Nullable Component text;
    private @Nullable UiAsset icon;
    private @Nullable Runnable action;
    private boolean pressed;
    private boolean selected;
    private boolean clickSound = true;

    public Button() {
        focusable(true);
        style().cursor(CursorTypes.POINTING_HAND).size(20, 20);
    }

    public static Button of(Component text, Runnable action) {
        return new Button().text(text).onPress(action);
    }

    public static Button icon(String iconName, Runnable action) {
        return new Button().icon(AssetLibrary.icon(iconName)).onPress(action);
    }

    public static Button icon(AssetKey icon, Runnable action) {
        return new Button().icon(AssetLibrary.get(icon)).onPress(action);
    }

    public Button skin(Skin skin) {
        this.skin = skin;
        return this;
    }

    public Button text(@Nullable Component text) {
        this.text = text;
        invalidateLayout();
        return this;
    }

    public @Nullable Component text() {
        return text;
    }

    public Button icon(@Nullable UiAsset icon) {
        this.icon = icon;
        invalidateLayout();
        return this;
    }

    public Button onPress(Runnable action) {
        this.action = action;
        return this;
    }

    public Button selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    public boolean isSelected() {
        return selected;
    }

    public Button clickSound(boolean clickSound) {
        this.clickSound = clickSound;
        return this;
    }

    public State state() {
        return new State(isEnabled(), isHovered(), isFocusVisible(), pressed, selected);
    }

    /** Runs the action as if clicked. */
    public void activate() {
        if (!isEnabled()) {
            return;
        }
        if (clickSound) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
        if (action != null) {
            action.run();
        }
    }

    @Override
    protected int contentWidth() {
        if (icon != null) {
            return icon.width();
        }
        return text == null ? 0 : Minecraft.getInstance().font.width(text) + TEXT_MARGIN * 2;
    }

    @Override
    protected int contentHeight(int contentWidth) {
        return icon != null ? icon.height() : 20;
    }

    @Override
    protected void paint(Canvas canvas, Frame frame) {
        Rect bounds = bounds();
        State state = state();
        skin.paint(canvas, bounds, state);
        int color = skin.contentColor(state);
        if (icon != null) {
            int w = icon.width();
            int h = icon.height();
            icon.paint(canvas, Rect.of(bounds.x() + bounds.width() / 2 - w / 2, bounds.y() + bounds.height() / 2 - h / 2, w, h), color);
        } else if (text != null) {
            int textWidth = canvas.textWidth(text);
            canvas.text(text, bounds.x() + bounds.width() / 2 - textWidth / 2, bounds.y() + (bounds.height() - 8) / 2, color, true);
        }
    }

    @Override
    protected void onPointer(PointerEvent event) {
        switch (event.type()) {
            case PRESS -> {
                if (event.isPrimary()) {
                    pressed = true;
                    event.consume();
                }
            }
            case RELEASE -> {
                if (pressed && event.isPrimary()) {
                    pressed = false;
                    event.consume();
                    if (bounds().contains(event.x(), event.y())) {
                        activate();
                    }
                }
            }
            case CANCEL -> pressed = false;
            default -> {
            }
        }
    }

    @Override
    protected void onKey(KeyEvent event) {
        if (event.type() == KeyEvent.Type.PRESS && isFocused()
            && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_SPACE || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            activate();
            event.consume();
        }
    }
}
