package com.ensox.ui.widget;

import com.ensox.base.Insets;
import com.ensox.ui.core.Column;
import com.ensox.ui.core.Container;
import com.ensox.ui.core.TransformContainer;
import com.ensox.ui.core.UiRoot;
import com.ensox.ui.core.Widget;
import com.ensox.ui.paint.Paint;
import com.ensox.ui.state.Signal;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class SelectPopupTest {
    private static final Select.Look LOOK = new Select.Look(Paint.NONE, Paint.NONE, Paint.NONE, Paint.NONE,
        Paint.NONE, Insets.of(6, 5), Paint.NONE, Insets.of(3, 2),
        (canvas, handle, track, vertical, visibility) -> { }, 8, 2, -2, 2, 20, 6, 0, 0, 0, Paint.NONE, 0, 0, 0, 0);

    private static KeyEvent key(int code) { return new KeyEvent(code, 0, 0); }

    private static UiRoot root(Widget widget) {
        UiRoot root = new UiRoot(widget);
        root.resize(400, 300);
        root.layoutIfNeeded();
        return root;
    }

    @Test void popupKeyboardDoesNotCommitUntilEnterAndRestoresAnchorBeforeCallback() {
        Signal<String> value = Signal.of("a");
        Select<String> select = Select.of(value, () -> List.of("a", "b", "c"), Component::literal, LOOK);
        UiRoot root = root(select);
        List<String> changes = new ArrayList<>();
        select.onChange(next -> {
            assertFalse(select.isOpen());
            assertSame(select, root.focus().focused(), "Inline form rebuilds need the anchor focused before onChange");
            changes.add(next);
        });
        root.focus().focus(select, true);
        root.keyPressed(key(InputConstants.KEY_RETURN));
        assertTrue(select.isOpen());
        root.keyPressed(key(InputConstants.KEY_DOWN));
        assertEquals("a", select.value());
        root.keyPressed(key(InputConstants.KEY_RETURN));
        assertEquals(List.of("b"), changes);
        assertEquals("b", select.value());
        assertTrue(root.focus().isFocusVisible());
    }

    @Test void escapeAndDetachCloseWithoutChangingTheValueOrLeavingFocusInThePopup() {
        Select<String> select = Select.of(Signal.of("a"), () -> List.of("a", "b"), Component::literal, LOOK)
            .searchable(3, () -> { throw new AssertionError("Small lists must not create a search input"); }, Component.empty());
        Column body = Column.of(select);
        UiRoot root = root(body);
        root.focus().focus(select, true);
        root.keyPressed(key(InputConstants.KEY_RETURN));
        root.keyPressed(key(InputConstants.KEY_DOWN));
        root.keyPressed(key(InputConstants.KEY_ESCAPE));
        assertFalse(select.isOpen());
        assertEquals("a", select.value());
        assertSame(select, root.focus().focused());
        root.keyPressed(key(InputConstants.KEY_RETURN));
        body.clear();
        assertTrue(root.overlays().isEmpty());
        assertNull(root.focus().focused());
    }

    @Test void filteredResultsScrollRebuildCountAndKeepTheirKeys() {
        List<String> items = IntStream.range(0, 30).mapToObj(i -> "option_" + i).toList();
        Select<String> select = Select.of(Signal.of(items.getFirst()), () -> items, Component::literal, LOOK);
        SelectOptions<String> choices = new SelectOptions<>(items, items.getLast(), value -> value);
        List<String> selected = new ArrayList<>();
        SelectPopup<String> content = new SelectPopup<>(select, LOOK, choices, true, Component.literal("No matches"),
            120, 66, selected::add);
        UiRoot root = root(content);
        root.focus().focus(content, true);
        ScrollView results = (ScrollView) content.children().getFirst();
        assertEquals(2, content.children().size(), "results and the count footer");
        content.revealHighlighted();
        assertTrue(results.scrollY() > 0);
        content.filter("nothing");
        root.keyPressed(key(InputConstants.KEY_RETURN));
        assertTrue(selected.isEmpty());
        content.filter("option_2");
        assertEquals(11, choices.visibleCount());
        assertEquals(30, choices.totalCount());
        assertEquals(List.of(new SelectOptions.Match(0, 8)), choices.matches("option_20"));
        root.keyPressed(key(InputConstants.KEY_DOWN));
        root.keyPressed(key(InputConstants.KEY_RETURN));
        assertEquals(List.of("option_20"), selected);
    }

    @Test void matchesOfSeveralTermsAreSortedAndMerged() {
        SelectOptions<String> choices = new SelectOptions<>(List.of("Stardust"), null, value -> value);
        choices.filter("ST dust");
        assertEquals(List.of(new SelectOptions.Match(0, 2), new SelectOptions.Match(4, 8)), choices.matches("Stardust"));
        choices.filter("sta tar");
        assertEquals(List.of(new SelectOptions.Match(0, 4)), choices.matches("Stardust"));
        choices.filter("");
        assertTrue(choices.matches("Stardust").isEmpty());
    }

    @Test void selectPopupUsesTheTransformedAnchorButKeepsReadableUnscaledContents() {
        Select<String> select = Select.of(Signal.of("a"), () -> List.of("a", "b"), Component::literal, LOOK);
        TransformContainer transform = new TransformContainer(select, 142);
        Container layer = new Container() {
            @Override protected void arrangeChildren() { transform.place(100, 60, .5F); }
        };
        layer.add(transform);
        layer.style().fill();
        UiRoot root = root(layer);
        root.focus().focus(select, true);
        root.keyPressed(key(InputConstants.KEY_RETURN));
        Widget content = root.overlays().getFirst().widget().children().getFirst();
        assertEquals(100, content.x());
        assertEquals(70, content.y());
        assertEquals(142, content.width());
        assertEquals(71, select.screenBounds().width());
    }
}
