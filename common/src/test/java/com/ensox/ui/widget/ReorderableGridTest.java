package com.ensox.ui.widget;

import com.ensox.ui.core.Container;
import com.ensox.ui.core.UiRoot;
import com.ensox.ui.core.Widget;
import com.ensox.ui.event.PointerEvent;
import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ReorderableGridTest {
    private record Item(String id, int revision) { }
    private static final class Control extends Widget {
        int presses;
        Control() { focusable(true); style().size(20, 20); }
        @Override protected void onPointer(PointerEvent event) {
            if (event.type() == PointerEvent.Type.PRESS) { presses++; event.consume(); }
            if (event.type() == PointerEvent.Type.RELEASE) event.consume();
        }
    }
    private static final class Card extends Container {
        final Control control = new Control();
        Card() { add(control); }
        @Override protected void arrangeChildren() {
            control.measure(20, 20); control.arrange(x() + width() - 24, y() + 4);
        }
    }

    private static List<Item> items(int count) {
        return IntStream.range(0, count).mapToObj(i -> new Item("i" + i, 0)).toList();
    }
    private static UiRoot root(ReorderableGrid<Item> grid) {
        UiRoot root = new UiRoot(grid); root.resize(340, 200); root.layoutIfNeeded(); return root;
    }

    @Test void onlyViewportCardsAreConstructedAndScrollingReleasesDistantWidgets() {
        AtomicInteger constructed = new AtomicInteger();
        var grid = new ReorderableGrid<>(Item::id, item -> { constructed.incrementAndGet(); return new Card(); })
            .cellSize(100, 80).items(items(10_000));
        UiRoot root = root(grid);
        assertTrue(constructed.get() <= 15);
        List<Widget> old = List.copyOf(grid.content().children());
        grid.scrollTo(0, 1000, false); grid.invalidateLayout(); root.layoutIfNeeded();
        assertTrue(grid.content().children().size() <= 18);
        assertTrue(old.stream().noneMatch(Widget::isAttached));
    }

    @Test void freeAreaDragCommitsOnceOnlyOnReleaseAndOutsideReleaseCancels() {
        var grid = new ReorderableGrid<>(Item::id, item -> new Card()).cellSize(100, 80).items(items(9));
        List<String> moves = new ArrayList<>();
        grid.onMove((moved, anchor, after) -> moves.add(moved + ":" + anchor + ":" + after));
        UiRoot root = root(grid);
        assertTrue(root.pointerPressed(10, 10, InputConstants.MOUSE_BUTTON_LEFT, 0, false));
        root.pointerDragged(130, 10, InputConstants.MOUSE_BUTTON_LEFT, 0, 120, 0);
        assertTrue(grid.dragging()); assertTrue(moves.isEmpty());
        root.pointerReleased(130, 10, InputConstants.MOUSE_BUTTON_LEFT, 0);
        assertEquals(List.of("i0:i1:true"), moves);
        root.pointerPressed(10, 10, InputConstants.MOUSE_BUTTON_LEFT, 0, false);
        root.pointerDragged(250, 10, InputConstants.MOUSE_BUTTON_LEFT, 0, 240, 0);
        root.pointerReleased(400, 10, InputConstants.MOUSE_BUTTON_LEFT, 0);
        assertEquals(1, moves.size()); assertFalse(grid.dragging());
    }

    @Test void interactivePressCannotTurnIntoCardDragEvenAfterLeavingTheControl() {
        var grid = new ReorderableGrid<>(Item::id, item -> new Card()).cellSize(100, 80).items(items(9));
        UiRoot root = root(grid);
        Card first = (Card) grid.content().children().getFirst();
        Control control = first.control;
        root.pointerPressed(control.x() + 2, control.y() + 2, InputConstants.MOUSE_BUTTON_LEFT, 0, false);
        root.pointerDragged(250, 30, InputConstants.MOUSE_BUTTON_LEFT, 0, 150, 0);
        root.pointerReleased(250, 30, InputConstants.MOUSE_BUTTON_LEFT, 0);
        assertEquals(1, control.presses); assertFalse(grid.dragging()); assertNull(grid.selected());
        control.enabled(false);
        root.pointerPressed(control.x() + 2, control.y() + 2, InputConstants.MOUSE_BUTTON_LEFT, 0, false);
        root.pointerDragged(250, 30, InputConstants.MOUSE_BUTTON_LEFT, 0, 150, 0);
        assertFalse(grid.dragging());
    }

    @Test void selectionOnlyCatalogCancelsMovedClicksWithoutReordering() {
        var grid = new ReorderableGrid<>(Item::id, item -> new Card()).cellSize(100, 80)
            .reorderable(false).items(items(9));
        List<String> selected = new ArrayList<>();
        grid.onSelect(selected::add).onMove((moved, anchor, after) -> fail("Catalog order is not editable"));
        UiRoot root = root(grid);
        root.pointerPressed(10, 10, InputConstants.MOUSE_BUTTON_LEFT, 0, false);
        root.pointerReleased(11, 10, InputConstants.MOUSE_BUTTON_LEFT, 0);
        assertEquals(List.of("i0"), selected);
        root.pointerPressed(130, 10, InputConstants.MOUSE_BUTTON_LEFT, 0, false);
        root.pointerDragged(250, 10, InputConstants.MOUSE_BUTTON_LEFT, 0, 120, 0);
        assertFalse(grid.dragging());
        root.pointerReleased(250, 10, InputConstants.MOUSE_BUTTON_LEFT, 0);
        assertEquals(List.of("i0"), selected);
        assertEquals("i0", grid.selected());
        root.keyPressed(new net.minecraft.client.input.KeyEvent(InputConstants.KEY_RIGHT, 0, 0));
        assertEquals("i1", grid.selected());
    }

    @Test void shortClickSelectsAndSnapshotReplacementKeepsFocusedControlByIdentity() {
        var grid = new ReorderableGrid<>(Item::id, item -> new Card()).cellSize(100, 80).items(items(9));
        UiRoot root = root(grid);
        root.pointerPressed(10, 10, InputConstants.MOUSE_BUTTON_LEFT, 0, false);
        root.pointerReleased(12, 11, InputConstants.MOUSE_BUTTON_LEFT, 0);
        assertEquals("i0", grid.selected());
        Card first = (Card) grid.content().children().getFirst();
        root.focus().focus(first.control, true);
        List<Item> revised = new ArrayList<>(items(9)); revised.set(0, new Item("i0", 1));
        grid.items(revised); root.layoutIfNeeded();
        assertEquals("i0", grid.selected());
        assertNotSame(first.control, root.focus().focused());
        assertInstanceOf(Control.class, root.focus().focused());
        assertTrue(root.focus().isFocusVisible());
        assertFalse(first.isAttached());
    }

    @Test void explicitCancelAndDatasetChangeNeverCommitAnOutstandingGesture() {
        var grid = new ReorderableGrid<>(Item::id, item -> new Card()).cellSize(100, 80).items(items(9));
        grid.onMove((moved, anchor, after) -> fail("cancelled drag must not persist"));
        UiRoot root = root(grid);
        root.pointerPressed(10, 10, InputConstants.MOUSE_BUTTON_LEFT, 0, false);
        root.pointerDragged(130, 10, InputConstants.MOUSE_BUTTON_LEFT, 0, 120, 0);
        grid.cancelDrag(); root.pointerReleased(130, 10, InputConstants.MOUSE_BUTTON_LEFT, 0);
        root.pointerPressed(10, 10, InputConstants.MOUSE_BUTTON_LEFT, 0, false);
        root.pointerDragged(130, 10, InputConstants.MOUSE_BUTTON_LEFT, 0, 120, 0);
        grid.items(items(3)); root.pointerReleased(130, 10, InputConstants.MOUSE_BUTTON_LEFT, 0);
        assertFalse(grid.dragging());
        assertThrows(IllegalArgumentException.class, () -> grid.items(List.of(new Item("x", 0), new Item("x", 1))));
    }

    @Test void escapeCancelsWithoutClosingScreenAndTabCannotFocusFloatingControls() {
        var grid = new ReorderableGrid<>(Item::id, item -> new Card()).cellSize(100, 80).items(items(9));
        grid.onMove((moved, anchor, after) -> fail("Escape must cancel the drop"));
        UiRoot root = root(grid);
        root.pointerPressed(10, 10, InputConstants.MOUSE_BUTTON_LEFT, 0, false);
        root.pointerDragged(130, 10, InputConstants.MOUSE_BUTTON_LEFT, 0, 120, 0);
        Widget focus = root.focus().focused();
        assertTrue(root.keyPressed(new net.minecraft.client.input.KeyEvent(InputConstants.KEY_TAB, 0, 0)));
        assertSame(focus, root.focus().focused());
        assertTrue(root.keyPressed(new net.minecraft.client.input.KeyEvent(InputConstants.KEY_ESCAPE, 0, 0)));
        assertFalse(grid.dragging());
        root.pointerReleased(130, 10, InputConstants.MOUSE_BUTTON_LEFT, 0);
    }

    @Test void scrollbarMeasurementAndVirtualMountingPreserveDragButActualResizeCancels() {
        var grid = new ReorderableGrid<>(Item::id, item -> new Card()).cellSize(100, 80).items(items(100));
        grid.scrollbar(new ScrollbarStyle() {
            @Override public int reservedSpace(int thickness) { return 3; }
            @Override public void paint(com.ensox.ui.paint.Canvas canvas, com.ensox.ui.geom.Rect handle,
                                        com.ensox.ui.geom.Rect track, boolean vertical, float visibility) { }
        });
        UiRoot root = root(grid);
        root.pointerPressed(10, 10, InputConstants.MOUSE_BUTTON_LEFT, 0, false);
        root.pointerDragged(130, 10, InputConstants.MOUSE_BUTTON_LEFT, 0, 120, 0);
        assertTrue(grid.dragging());
        grid.invalidateLayout(); root.layoutIfNeeded();
        assertTrue(grid.dragging(), "ScrollView's intermediate full-width probe must not cancel a drag");
        grid.scrollTo(0, 1000, false); grid.invalidateLayout(); root.layoutIfNeeded();
        root.layoutIfNeeded(); // Mounting the newly visible rows requested another pass.
        assertTrue(grid.dragging(), "Virtualized row mounting is not a viewport resize");
        root.resize(350, 200); root.layoutIfNeeded();
        assertFalse(grid.dragging(), "A real geometry change cancels the outstanding gesture");
    }
}
