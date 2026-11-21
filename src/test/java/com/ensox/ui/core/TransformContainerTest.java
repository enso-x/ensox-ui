package com.ensox.ui.core;

import com.ensox.ui.event.PointerEvent;
import com.ensox.ui.event.ScrollEvent;
import com.ensox.ui.event.TextEvent;
import com.ensox.ui.event.UiEvent;
import com.ensox.ui.geom.Rect;
import com.ensox.ui.geom.UiTransform;
import com.ensox.ui.widget.Popup;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransformContainerTest {
    private static final class Probe extends Widget {
        final List<PointerEvent> pointers = new ArrayList<>();
        ScrollEvent scroll;
        int text;
        int measures;
        Probe() { style().size(100, 40); focusable(true); }
        @Override protected void onPointer(PointerEvent event) { pointers.add(event); event.consume(); }
        @Override protected void onScroll(ScrollEvent event) { scroll = event; event.consume(); }
        @Override protected void onText(TextEvent event) { text++; event.consume(); }
        @Override protected void measureSelf(int w, int h, int sw, int sh) { measures++; super.measureSelf(w, h, sw, sh); }
    }
    private static final class Layer extends Container {
        final TransformContainer surface;
        boolean interceptDrag;
        double interceptedX;
        Layer(Probe probe) { surface = new TransformContainer(probe, 100); add(surface); style().fill(); }
        @Override protected void arrangeChildren() { surface.place(120, 60, .5F); }
        @Override protected void intercept(UiEvent event) {
            if (interceptDrag && event instanceof PointerEvent pointer && pointer.type() == PointerEvent.Type.DRAG) {
                interceptedX = pointer.x(); event.consume();
            }
        }
    }
    private static UiRoot root(Layer layer) {
        UiRoot root = new UiRoot(layer); root.resize(400, 300); root.layoutIfNeeded(); return root;
    }

    @Test void zoomedFieldsReceiveLogicalCoordinatesAndCaptureOutsideTheirBounds() {
        Probe field = new Probe(); Layer layer = new Layer(field); UiRoot root = root(layer);
        assertSame(field, layer.hitTest(145, 70));
        assertEquals(Rect.of(120, 60, 50, 20), field.screenBounds());
        assertTrue(root.pointerPressed(145, 70, 0, 0, false));
        assertSame(field, root.focus().focused());
        assertTrue(root.pointerDragged(230, 90, 0, 0, 20, 10));
        assertTrue(root.pointerReleased(230, 90, 0, 0));
        assertEquals(50, field.pointers.getFirst().x());
        assertEquals(20, field.pointers.getFirst().y());
        assertEquals(220, field.pointers.get(1).x());
        assertEquals(40, field.pointers.get(1).deltaX());
        assertEquals(20, field.pointers.get(1).deltaY());
        assertSame(field, field.pointers.getFirst().target());
        assertTrue(root.textTyped('x', 0));
        assertEquals(1, field.text);
    }

    @Test void scrollAndInterceptedCancellationUseEachReceiversCoordinateSpace() {
        Probe field = new Probe(); Layer layer = new Layer(field); UiRoot root = root(layer);
        assertTrue(root.scrolled(135, 65, 2, -3));
        assertEquals(30, field.scroll.x()); assertEquals(10, field.scroll.y());
        assertEquals(-3, field.scroll.amountY(), "Wheel steps must not be scaled as pixel distances");
        root.pointerPressed(145, 70, 0, 0, false);
        layer.interceptDrag = true;
        root.pointerDragged(160, 80, 0, 0, 15, 10);
        assertEquals(160, layer.interceptedX);
        assertEquals(PointerEvent.Type.CANCEL, field.pointers.getLast().type());
        assertEquals(80, field.pointers.getLast().x());
    }

    @Test void popupAnchorsAndTranslationKeepLogicalChildLayout() {
        Probe field = new Probe(); Layer layer = new Layer(field); UiRoot root = root(layer);
        Widget content = new Widget() { }; content.style().size(70, 30);
        Popup popup = Popup.open(field, content, null, null);
        root.layoutIfNeeded();
        assertEquals(120, content.x()); assertEquals(80, content.y());
        assertSame(content, popup.hitTest(130, 90));
        layer.surface.translate(30, 15);
        assertEquals(0, field.x()); assertEquals(0, field.y());
        assertEquals(Rect.of(150, 75, 50, 20), field.screenBounds());
        popup.close();
        assertFalse(popup.isOpen());
    }

    @Test void nestedTransformsRoundBoundsOutwardAndInvertExactly() {
        UiTransform result = new UiTransform(10, 20, .5).then(new UiTransform(8, 6, 1.5));
        assertEquals(14, result.x()); assertEquals(23, result.y()); assertEquals(.75, result.scale());
        assertEquals(13.5, result.unmapX(result.mapX(13.5)));
        assertEquals(Rect.of(14, 23, 8, 8), result.map(Rect.of(0, 0, 10, 10)));
        assertThrows(IllegalArgumentException.class, () -> new UiTransform(0, 0, 0));
    }

    @Test void panAndZoomReuseLayoutWhileFieldChangesInvalidateTheMeasurement() {
        Probe field = new Probe(); Layer layer = new Layer(field); UiRoot root = root(layer);
        int measured = field.measures;
        root.focus().focus(field);
        for (int i = 0; i < 30; i++) layer.surface.place(120 + i, 60 - i, .5F + i * .01F);
        assertEquals(measured, field.measures, "Viewport updates must not relayout structured fields");
        assertSame(field, root.focus().focused());
        field.style().height(com.ensox.ui.layout.Sizing.fixed(70));
        layer.surface.place(130, 70, 1);
        assertEquals(measured + 1, field.measures);
        assertEquals(70, layer.surface.logicalHeight());
    }
}
