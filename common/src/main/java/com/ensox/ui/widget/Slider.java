package com.ensox.ui.widget;

import com.ensox.ui.core.Widget;
import com.ensox.ui.event.KeyEvent;
import com.ensox.ui.event.PointerEvent;
import com.ensox.ui.geom.Rect;
import com.ensox.ui.paint.Canvas;
import com.ensox.ui.state.Signal;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.function.DoubleConsumer;

/**
 * Value slider over {@code [min, max]} with step snapping.
 *
 * <p>Bound to a {@code Signal<Double>}, which is the single source of truth:
 * dragging writes the signal, and external changes move the handle. Arrow keys
 * step when focused (Shift = 10 steps).</p>
 */
public class Slider extends Widget {
    /** Draws track and handle for a 0..1 position. */
    public interface Skin {
        void paint(Canvas canvas, Rect bounds, double ratio, boolean vertical, boolean highlighted);

        /** Handle length along the slider axis, used to map the pointer to a value. */
        int handleLength(boolean vertical);

        /** Natural thickness across the axis (content size). */
        int thickness(boolean vertical);
    }

    private final boolean vertical;
    private final Skin skin;
    private double min;
    private double max = 1;
    private double step;
    private double value;
    private @Nullable DoubleConsumer onChange;
    private @Nullable Runnable onRelease;
    private boolean dragging;

    public Slider(boolean vertical, Skin skin) {
        this.vertical = vertical;
        this.skin = skin;
        focusable(true);
        style().cursor(vertical ? CursorTypes.RESIZE_NS : CursorTypes.RESIZE_EW);
    }

    /** Two-way binding to {@code value}. */
    public static Slider of(Signal<Double> value, double min, double max, double step, boolean vertical, Skin skin) {
        Slider slider = new Slider(vertical, skin).range(min, max, step);
        slider.bind(value, next -> slider.setValue(next == null ? min : next, false));
        slider.onChange(value::set);
        return slider;
    }

    public Slider range(double min, double max, double step) {
        this.min = min;
        this.max = Math.max(min, max);
        this.step = Math.max(0, step);
        this.value = snap(value);
        return this;
    }

    public Slider onChange(DoubleConsumer onChange) {
        this.onChange = onChange;
        return this;
    }

    /** Fires when the user releases the handle, for committing expensive work once. */
    public Slider onRelease(Runnable onRelease) {
        this.onRelease = onRelease;
        return this;
    }

    public double value() {
        return value;
    }

    public void value(double value) {
        setValue(value, false);
    }

    private void setValue(double next, boolean notify) {
        double snapped = snap(next);
        if (Double.compare(snapped, value) == 0) {
            return;
        }
        value = snapped;
        if (notify && onChange != null) {
            onChange.accept(value);
        }
    }

    /** Clamp, snap to {@code min + k * step}, then round to the step's decimal scale to drop float noise. */
    public double snap(double raw) {
        double clamped = Math.max(min, Math.min(max, raw));
        if (step <= 0) {
            return clamped;
        }
        double snapped = Math.max(min, Math.min(max, min + Math.round((clamped - min) / step) * step));
        return BigDecimal.valueOf(snapped).setScale(stepScale(step), RoundingMode.HALF_UP).doubleValue();
    }

    /** Float precision: steps rarely need more, and widened floats carry noise beyond it. */
    private static final MathContext STEP_PRECISION = new MathContext(7);

    /**
     * Decimal places of {@code step}, ignoring float widening noise: many steps
     * come from {@code float} metadata, and {@code 0.01F} widened to double is
     * {@code 0.009999999776...}, which would otherwise mean 18 decimals and make
     * snapped values and text fields show {@code 0.1999999955...} for {@code 0.2}.
     */
    public static int stepScale(double step) {
        if (!(step > 0) || !Double.isFinite(step)) {
            return 0;
        }
        return Math.max(0, BigDecimal.valueOf(step).round(STEP_PRECISION).stripTrailingZeros().scale());
    }

    public double ratio() {
        return max == min ? 0 : (value - min) / (max - min);
    }

    @Override
    protected int contentWidth() {
        return vertical ? skin.thickness(true) : 64;
    }

    @Override
    protected int contentHeight(int contentWidth) {
        return vertical ? 64 : skin.thickness(false);
    }

    @Override
    protected void paint(Canvas canvas, Frame frame) {
        skin.paint(canvas, bounds(), ratio(), vertical, isHovered() || dragging || isFocusVisible());
    }

    private void setFromPointer(double px, double py) {
        Rect b = bounds();
        int handle = skin.handleLength(vertical);
        double range = Math.max(1, (vertical ? b.height() : b.width()) - handle);
        double offset = (vertical ? py - b.y() : px - b.x()) - handle / 2d;
        double ratio = Math.max(0, Math.min(1, offset / range));
        setValue(min + ratio * (max - min), true);
    }

    @Override
    protected void onPointer(PointerEvent event) {
        switch (event.type()) {
            case PRESS -> {
                if (event.isPrimary()) {
                    dragging = true;
                    setFromPointer(event.x(), event.y());
                    event.consume();
                }
            }
            case DRAG -> {
                if (dragging) {
                    setFromPointer(event.x(), event.y());
                    event.consume();
                }
            }
            case RELEASE -> {
                if (dragging) {
                    dragging = false;
                    event.consume();
                    if (onRelease != null) {
                        onRelease.run();
                    }
                }
            }
            case CANCEL -> dragging = false;
            default -> {
            }
        }
    }

    @Override
    protected void onKey(KeyEvent event) {
        if (event.type() != KeyEvent.Type.PRESS || !isFocused()) {
            return;
        }
        double unit = (step > 0 ? step : (max - min) / 100) * (event.modifiers().shift() ? 10 : 1);
        int direction = switch (event.key()) {
            case InputConstants.KEY_LEFT, InputConstants.KEY_DOWN -> -1;
            case InputConstants.KEY_RIGHT, InputConstants.KEY_UP -> 1;
            default -> 0;
        };
        if (direction != 0) {
            setValue(value + direction * unit, true);
            event.consume();
        }
    }
}
