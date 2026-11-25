package com.ensox.ui.paint;

import com.ensox.ui.core.Column;
import com.ensox.ui.core.UiRoot;
import com.ensox.ui.core.Widget;
import com.ensox.ui.geom.Rect;
import com.ensox.ui.layout.Position;
import com.ensox.ui.widget.ScrollView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScrollViewPixelAlignmentTest {
    private final Canvas canvas = new Canvas(null, null);
    private Rect painted;
    private final Widget card = new Widget() { };
    private final ScrollView scroll;
    private final UiRoot root;

    ScrollViewPixelAlignmentTest() {
        card.style(s -> s.size(20, 20).position(Position.absolute(24, 24))
            .background((surface, bounds) -> painted = bounds));
        // Capture paint bounds without a Minecraft window. No text, GPU scissors or scrollbar sprites are needed.
        Column content = Column.of(card).style(s -> s.size(160, 160).overflow());
        scroll = ScrollView.both(content).scrollbar((surface, handle, track, vertical, visibility) -> {});
        scroll.style().fill().overflow();
        root = new UiRoot(scroll);
        root.resize(64, 64);
        root.layoutIfNeeded();
    }

    @Test void fractionalStopsUseTheSamePixelForLayoutPaintingAndHitTesting() {
        scroll.scrollTo(2.25, 3.75, false);
        root.requestLayout();
        root.layoutIfNeeded();
        assertEquals(Rect.of(22, 20, 20, 20), card.bounds());
        renderAndCheck(0, 22, 20);

        // Fractional input accumulates even when an individual step does not move the displayed card.
        scroll.scrollBy(.125, .125, false);
        renderAndCheck(0, 22, 20);
        scroll.scrollBy(.25, .25, false);
        renderAndCheck(0, 21, 20);
        assertEquals(2.625, scroll.scrollX());
        assertEquals(4.125, scroll.scrollY());
    }

    @Test void easedWheelScrollMovesThroughWholePixelsAndKeepsItsFractionalTarget() {
        assertTrue(root.scrolled(32, 32, -.25, -.5));
        assertEquals(3.75, scroll.scrollX());
        assertEquals(7.5, scroll.scrollY());
        renderAndCheck(.03F, 23, 22);
        renderAndCheck(1, 20, 16);
        renderAndCheck(0, 20, 16);
    }

    private void renderAndCheck(float deltaSeconds, int x, int y) {
        painted = null;
        scroll.render(canvas, new Widget.Frame(0, 0, 0, deltaSeconds));
        assertEquals(Rect.of(x, y, 20, 20), painted);
        assertSame(card, scroll.hitTest(painted.right() - .25, painted.bottom() - .25));
        assertNotSame(card, scroll.hitTest(painted.right(), painted.bottom() - .25));
        assertNotSame(card, scroll.hitTest(painted.right() - .25, painted.bottom()));
    }
}
