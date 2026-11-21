package com.ensox.ui.paint;

import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TextFitTest {
    private static final FontDescription CUSTOM_FONT = new FontDescription.Resource(Identifier.parse("test:wide"));
    private static final StringSplitter METRICS = new StringSplitter((codepoint, style) ->
        CUSTOM_FONT.equals(style.getFont()) ? 3F : style.isBold() ? 2F : 1F);

    @Test void fittingPreservesNestedStylesAndUsesTheirActualWidths() {
        Style styled = Style.EMPTY.withBold(true).withColor(0xFFAABB).withFont(CUSTOM_FONT);
        Component original = Component.literal("A").append(Component.literal("BCDEF").withStyle(styled));
        FormattedText fitted = TextFit.fit(METRICS, original, 7);
        List<Style> styles = new ArrayList<>();
        fitted.visit((style, text) -> {
            if (text.contains("B")) styles.add(style);
            return Optional.empty();
        }, Style.EMPTY);

        assertEquals("AB...", fitted.getString());
        assertEquals(7F, METRICS.stringWidth(fitted));
        assertEquals(1, styles.size());
        assertTrue(styles.getFirst().isBold());
        assertEquals(CUSTOM_FONT, styles.getFirst().getFont());
        assertEquals(0xFFAABB, styles.getFirst().getColor().getValue());
        assertEquals("ABCDEF", original.getString(), "fitting never changes the original label");
    }

    @Test void ellipsisInheritsTheBaseStyleAndNeverExceedsItsBudget() {
        Component bold = Component.literal("ABCDEFG").withStyle(Style.EMPTY.withBold(true));
        FormattedText fitted = TextFit.fit(METRICS, bold, 10);
        assertEquals("AB...", fitted.getString());
        fitted.visit((style, text) -> {
            assertTrue(style.isBold());
            return Optional.empty();
        }, Style.EMPTY);

        for (int width = 0; width <= 16; width++) {
            assertTrue(METRICS.stringWidth(TextFit.fit(METRICS, bold, width)) <= width);
        }
        assertEquals("", TextFit.fit(METRICS, bold, -5).getString());
        assertEquals("", TextFit.fit(METRICS, bold, 5).getString());
    }

    @Test void exactFitReturnsTheOriginalAndTruncationKeepsWholeCodepoints() {
        Component label = Component.literal("A😀BCDEFG");
        assertSame(label, TextFit.fit(METRICS, label, 8));
        assertEquals("A😀...", TextFit.fit(METRICS, label, 5).getString());
    }
}
