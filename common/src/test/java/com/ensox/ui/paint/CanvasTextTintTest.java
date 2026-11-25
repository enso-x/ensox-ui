package com.ensox.ui.paint;

import com.ensox.base.ColorX;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CanvasTextTintTest {
    @Test void explicitSpanAndShadowColorsAreTintedWithoutApplyingAlphaTwice() {
        Canvas canvas = new Canvas(null, null).withColor(ColorX.of(0x80FF8080));
        Style original = Style.EMPTY.withColor(0xC86432).withShadowColor(0x4080C864).withBold(true).withItalic(true);
        List<Style> styles = new ArrayList<>();

        assertTrue(canvas.tintText(FormattedCharSequence.forward("ab", original)).accept((index, style, codepoint) -> {
            styles.add(style);
            return true;
        }));

        assertEquals(0xC83219, styles.getFirst().getColor().getValue());
        assertEquals(0x40806432, styles.getFirst().getShadowColor());
        assertTrue(styles.getFirst().isBold());
        assertTrue(styles.getFirst().isItalic());
        assertSame(styles.getFirst(), styles.getLast(), "one transformed style per span, not per glyph");
        assertEquals(0xC86432, original.getColor().getValue());
        assertEquals(0x4080C864, original.getShadowColor());
        assertEquals(0x40C83219, canvas.tintColor(0x80C86432), "text alpha is multiplied only in the default color");
    }

    @Test void defaultStylesAndAlphaOnlyViewsKeepVanillaStyleSemantics() {
        Style bold = Style.EMPTY.withBold(true);
        FormattedCharSequence plain = FormattedCharSequence.forward("a", bold);
        Canvas colored = new Canvas(null, null).withColor(ColorX.of(0x80FF8080));
        assertTrue(colored.tintText(plain).accept((index, style, codepoint) -> {
            assertSame(bold, style);
            assertNull(style.getColor());
            assertNull(style.getShadowColor());
            return true;
        }));
        assertSame(plain, new Canvas(null, null).tintText(plain));
        assertSame(plain, new Canvas(null, null).withColor(ColorX.of(0x00FFFFFF)).tintText(plain));
    }

    @Test void modulationPreservesVisualOrderIndicesCodepointsAndSinkCancellation() {
        List<Integer> visited = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        FormattedCharSequence visualOrder = sink -> sink.accept(7, Style.EMPTY.withColor(0xFFFFFF), 0x05D1)
            && sink.accept(2, Style.EMPTY.withColor(0xFFFFFF), 0x1F600)
            && sink.accept(9, Style.EMPTY, 'x');
        Canvas canvas = new Canvas(null, null).withColor(ColorX.of(0xFFFF0000));

        assertFalse(canvas.tintText(visualOrder).accept((index, style, codepoint) -> {
            indices.add(index);
            visited.add(codepoint);
            assertEquals(0xFF0000, style.getColor().getValue());
            return visited.size() < 2;
        }));
        assertEquals(List.of(7, 2), indices);
        assertEquals(List.of(0x05D1, 0x1F600), visited);
    }
}
