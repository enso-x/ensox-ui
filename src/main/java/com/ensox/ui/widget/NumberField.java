package com.ensox.ui.widget;

import com.ensox.ui.core.Row;
import com.ensox.ui.event.ScrollEvent;
import com.ensox.ui.layout.Align;
import com.ensox.ui.state.Signal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Slider plus numeric text input bound to one {@code Signal<Double>}.
 *
 * <ul>
 *     <li>The signal is the single source of truth; slider and text both
 *     follow it, so they can never disagree.</li>
 *     <li>Typing accepts {@code .} or {@code ,}; the value is applied on Enter
 *     or when the field loses focus, clamped to the range and snapped to the
 *     step. Invalid text reverts to the current value.</li>
 *     <li>The mouse wheel steps the value while the text field is focused
 *     (Shift = 1/10 step, Ctrl = 10 steps), so scrolling a settings page never
 *     changes values by accident.</li>
 *     <li>{@link Format#percent} shows 0..1 as 0..100 with a {@code %} suffix.</li>
 * </ul>
 */
public class NumberField extends Row {
    /** How the value is displayed and parsed. */
    public record Format(int decimals, double displayScale, String suffix) {
        public static Format decimals(int decimals) {
            return new Format(Math.max(0, decimals), 1, "");
        }

        public static Format percent(int decimals) {
            return new Format(Math.max(0, decimals), 100, "%");
        }

        public Format withSuffix(String suffix) {
            return new Format(decimals, displayScale, suffix);
        }

        public String format(double value) {
            String number = BigDecimal.valueOf(value * displayScale).setScale(decimals, RoundingMode.HALF_UP).toPlainString();
            return suffix.isEmpty() ? number : number + suffix;
        }

        /** @return the parsed value in storage units, or {@code null} for invalid input */
        public Double parse(String text) {
            String cleaned = text.trim().replace(',', '.');
            if (!suffix.isEmpty() && cleaned.endsWith(suffix)) {
                cleaned = cleaned.substring(0, cleaned.length() - suffix.length()).trim();
            }
            try {
                double parsed = Double.parseDouble(cleaned);
                return Double.isFinite(parsed) ? parsed / displayScale : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        /** Display decimals for a step: 0.01 -> 2, 1 -> 0, 0.5 -> 1. */
        public static int decimalsForStep(double step) {
            if (step <= 0) {
                return 2;
            }
            return Slider.stepScale(step);
        }
    }

    private final Signal<Double> value;
    private final Slider slider;
    private final TextField input;
    private final Format format;

    public NumberField(Signal<Double> value, double min, double max, double step, Format format,
                       Slider.Skin sliderSkin, TextField input) {
        this.value = value;
        this.format = format;
        this.slider = Slider.of(value, min, max, step, false, sliderSkin);
        this.input = input;
        input.filter(text -> text.length() <= 24 && text.chars().allMatch(c -> Character.isDigit(c)
            || c == '.' || c == ',' || c == '-' || c == '+' || c == 'e' || c == 'E' || format.suffix().indexOf(c) >= 0));
        input.onCommit(this::commitText);
        input.bind(value, next -> {
            if (!input.isFocused()) {
                input.text(format.format(next == null ? min : next));
            }
        });
        input.onScrollEvent(this::onWheel);
        add(slider, input);
        style(s -> s.gap(4).alignY(Align.CENTER));
    }

    public Slider slider() {
        return slider;
    }

    public TextField input() {
        return input;
    }

    private void commitText(String text) {
        Double parsed = format.parse(text);
        if (parsed != null) {
            value.set(slider.snap(parsed));
        }
        // Always show the canonical form of the stored value (also reverts invalid text).
        input.text(format.format(value.get()));
    }

    private void onWheel(ScrollEvent event) {
        if (!input.isFocused() || event.amountY() == 0) {
            return;
        }
        double step = stepOf();
        double factor = event.modifiers().shift() ? 0.1 : event.modifiers().control() ? 10 : 1;
        value.set(slider.snap(value.get() + Math.signum(event.amountY()) * step * factor));
        input.text(format.format(value.get()));
        input.selectAll();
        event.consume();
    }

    private double stepOf() {
        // The slider snaps; use its resolution as the wheel unit.
        double probe = slider.snap(value.get() + 1e-9) - value.get();
        double formatUnit = Math.pow(10, -format.decimals()) / format.displayScale();
        return Math.max(Math.abs(probe), formatUnit);
    }

    @Override
    public String toString() {
        return "NumberField[" + String.format(Locale.ROOT, "%s", value.get()) + "]";
    }
}
