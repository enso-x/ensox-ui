package com.ensox.ui.widget;

import com.ensox.base.Easing;
import com.ensox.ui.core.Widget;
import com.ensox.ui.event.KeyEvent;
import com.ensox.ui.event.PointerEvent;
import com.ensox.ui.geom.Rect;
import com.ensox.ui.paint.Canvas;
import com.ensox.ui.state.Signal;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.function.Consumer;

/**
 * On/off switch bound to a {@code Signal<Boolean>}. The skin receives an
 * animated 0..1 progress, so the knob slides between positions.
 */
public class Toggle extends Widget {
    public interface Skin {
        /** @param progress 0 = off, 1 = on, in between while animating */
        void paint(Canvas canvas, Rect bounds, boolean on, float progress, boolean highlighted);

        int width();

        int height();
    }

    private static final float ANIMATION_SECONDS = 0.1F;

    private final Skin skin;
    private boolean on;
    private float progress;
    private @Nullable Consumer<Boolean> onChange;
    private SoundEvent sound = SoundEvents.UI_BUTTON_CLICK.value();

    public Toggle(Skin skin) {
        this.skin = skin;
        focusable(true);
        style().cursor(CursorTypes.POINTING_HAND);
    }

    public static Toggle of(Signal<Boolean> value, Skin skin) {
        Toggle toggle = new Toggle(skin);
        toggle.bind(value, next -> toggle.set(Boolean.TRUE.equals(next), false, !toggle.isAttached()));
        toggle.onChange(value::set);
        return toggle;
    }

    public Toggle onChange(Consumer<Boolean> onChange) {
        this.onChange = onChange;
        return this;
    }

    public Toggle sound(SoundEvent sound) {
        this.sound = sound;
        return this;
    }

    public boolean isOn() {
        return on;
    }

    public void toggle() {
        set(!on, true, false);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F));
    }

    private void set(boolean next, boolean notify, boolean instant) {
        if (on == next) {
            return;
        }
        on = next;
        if (instant) {
            progress = on ? 1 : 0;
        }
        if (notify && onChange != null) {
            onChange.accept(on);
        }
    }

    @Override
    protected int contentWidth() {
        return skin.width();
    }

    @Override
    protected int contentHeight(int contentWidth) {
        return skin.height();
    }

    @Override
    protected void paint(Canvas canvas, Frame frame) {
        float target = on ? 1 : 0;
        if (progress != target) {
            float stepAmount = frame.deltaSeconds() / ANIMATION_SECONDS;
            progress = on ? Math.min(1, progress + stepAmount) : Math.max(0, progress - stepAmount);
        }
        skin.paint(canvas, bounds(), on, Easing.EASE_IN_OUT.apply(progress), isHovered() || isFocusVisible());
    }

    @Override
    protected void onPointer(PointerEvent event) {
        if (event.type() == PointerEvent.Type.PRESS && event.isPrimary()) {
            toggle();
            event.consume();
        }
    }

    @Override
    protected void onKey(KeyEvent event) {
        if (event.type() == KeyEvent.Type.PRESS && isFocused()
            && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER || event.key() == InputConstants.KEY_SPACE)) {
            toggle();
            event.consume();
        }
    }
}
