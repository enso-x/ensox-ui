package com.ensox.ui.paint;

import com.ensox.ui.geom.Rect;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;

/**
 * Single-line text in a fixed width: drawn as is when it fits, truncated with
 * an ellipsis when it does not, or scrolled back and forth (marquee) while
 * {@code animate} is set (typically on hover).
 */
public final class TextFit {
    private static final int MARQUEE_PIXELS_PER_SECOND = 24;
    private static final long MARQUEE_PAUSE_MS = 250;

    private TextFit() {
    }

    /**
     * Fits a single styled line, preserving font/bold/color spans and applying
     * the component's base style to the ellipsis. Returns empty when even the
     * ellipsis cannot fit. The result remains in logical order for vanilla bidi.
     */
    public static FormattedText fit(Font font, Component text, int width) {
        return fit(font.getSplitter(), text, width);
    }

    static FormattedText fit(StringSplitter splitter, Component text, int width) {
        if (width <= 0) return FormattedText.EMPTY;
        if (splitter.stringWidth(text) <= width) return text;
        FormattedText ellipsis = FormattedText.of("...", text.getStyle());
        int ellipsisWidth = Mth.ceil(splitter.stringWidth(ellipsis));
        if (ellipsisWidth > width) return FormattedText.EMPTY;
        return FormattedText.composite(
            splitter.headByWidth(text, width - ellipsisWidth, Style.EMPTY), ellipsis);
    }

    public static void draw(Canvas canvas, String text, int x, int y, int width, int argb, boolean shadow, boolean animate) {
        if (width <= 0) {
            return;
        }
        Font font = canvas.font();
        int fullWidth = font.width(text);
        if (fullWidth <= width) {
            canvas.text(text, x, y, argb, shadow);
            return;
        }
        if (!animate) {
            String truncated = font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
            canvas.text(truncated, x, y, argb, shadow);
            return;
        }
        int overflow = fullWidth - width;
        canvas.pushClip(Rect.of(x, y - 1, width, font.lineHeight + 2));
        canvas.text(text, x - marqueeOffset(overflow), y, argb, shadow);
        canvas.popClip();
    }

    /** Pause, travel to the end, pause, travel back; repeats. */
    static int marqueeOffset(int overflow) {
        long travelMs = Math.max(1, overflow * 1000L / MARQUEE_PIXELS_PER_SECOND);
        long cycle = 2 * (MARQUEE_PAUSE_MS + travelMs);
        long t = System.currentTimeMillis() % cycle;
        if (t < MARQUEE_PAUSE_MS) {
            return 0;
        }
        t -= MARQUEE_PAUSE_MS;
        if (t < travelMs) {
            return Mth.clamp((int) (t * overflow / travelMs), 0, overflow);
        }
        t -= travelMs;
        if (t < MARQUEE_PAUSE_MS) {
            return overflow;
        }
        t -= MARQUEE_PAUSE_MS;
        return Mth.clamp(overflow - (int) (t * overflow / travelMs), 0, overflow);
    }
}
