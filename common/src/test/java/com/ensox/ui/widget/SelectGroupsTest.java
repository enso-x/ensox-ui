package com.ensox.ui.widget;

import com.ensox.base.Insets;
import com.ensox.ui.core.UiRoot;
import com.ensox.ui.core.Widget;
import com.ensox.ui.geom.Rect;
import com.ensox.ui.paint.Paint;
import com.ensox.ui.state.Signal;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SelectGroupsTest {
    private static final Select.Look LOOK = new Select.Look(Paint.NONE, Paint.NONE, Paint.NONE, Paint.NONE,
        Paint.NONE, Insets.of(6, 5), Paint.NONE, Insets.of(3, 2),
        (canvas, handle, track, vertical, visibility) -> { }, 8, 2, -2, 2, 20, 6, 0, 0, 0, Paint.NONE, 0, 0, 0, 2);

    private static final SelectGroup ITEM = new SelectGroup("item", Component.literal("Item"));
    private static final SelectGroup SMITHERY = new SelectGroup("smithery", Component.literal("Smithery"));
    private static final Map<String, SelectGroup> GROUPS = Map.of(
        "itemId", ITEM, "rarity", ITEM, "smithery:rarity", SMITHERY, "smithery:sharpening", SMITHERY);
    private static final List<String> OPTIONS = List.of("itemId", "rarity", "selector", "smithery:rarity", "smithery:sharpening");

    private static KeyEvent key(int code) { return new KeyEvent(code, 0, 0); }

    private static UiRoot root(Widget widget) {
        UiRoot root = new UiRoot(widget);
        root.resize(400, 300);
        root.layoutIfNeeded();
        return root;
    }

    private static SelectRows<String> rows(String selected) {
        return new SelectRows<>(new SelectOptions<>(OPTIONS, selected, value -> GROUPS.containsKey(value)
            ? GROUPS.get(value).label().getString() + " " + value : value), GROUPS::get);
    }

    @Test void treeKeepsFirstAppearanceOrderAndHighlightsTheValuesCategory() {
        SelectRows<String> rows = rows("smithery:sharpening");
        assertTrue(rows.isTree());
        List<SelectRows.Row<String>> tree = rows.rows();
        assertEquals(3, tree.size());
        assertEquals(new SelectRows.Group<>(ITEM, List.of("itemId", "rarity")), tree.get(0));
        assertEquals(new SelectRows.Option<>("selector", null), tree.get(1));
        assertEquals(new SelectRows.Group<>(SMITHERY, List.of("smithery:rarity", "smithery:sharpening")), tree.get(2));
        assertEquals(2, rows.highlightedIndex());
    }

    @Test void filteringFlattensIntoPrefixedOptionsAndFindsCategoryNames() {
        SelectRows<String> rows = rows("itemId");
        rows.filter("smith");
        assertFalse(rows.isTree());
        assertEquals(List.of(new SelectRows.Option<>("smithery:rarity", SMITHERY),
            new SelectRows.Option<>("smithery:sharpening", SMITHERY)), rows.rows());
        rows.filter("");
        assertTrue(rows.isTree());
    }

    @Test void keyboardOpensASubmenuChoosesFromItAndClosesBothLists() {
        Signal<String> value = Signal.of("itemId");
        Select<String> select = Select.of(value, () -> OPTIONS, Component::literal, LOOK).grouped(GROUPS::get);
        UiRoot root = root(select);
        root.focus().focus(select, true);
        root.keyPressed(key(InputConstants.KEY_RETURN));
        assertTrue(select.isOpen());
        assertEquals(1, root.overlays().size());
        root.keyPressed(key(InputConstants.KEY_DOWN));
        root.keyPressed(key(InputConstants.KEY_DOWN));   // Smithery
        root.keyPressed(key(InputConstants.KEY_RIGHT));
        assertEquals(2, root.overlays().size(), "the submenu is an overlay of its own");
        root.keyPressed(key(InputConstants.KEY_LEFT));
        assertEquals(1, root.overlays().size(), "Left returns to the categories");
        root.keyPressed(key(InputConstants.KEY_RIGHT));
        root.keyPressed(key(InputConstants.KEY_DOWN));
        root.keyPressed(key(InputConstants.KEY_RETURN));
        assertEquals("smithery:sharpening", select.value());
        assertFalse(select.isOpen());
        assertTrue(root.overlays().isEmpty());
        assertSame(select, root.focus().focused());
    }

    @Test void escapeClosesTheSubmenuFirstAndAnOutsideClickClosesEverything() {
        Select<String> select = Select.of(Signal.of("itemId"), () -> OPTIONS, Component::literal, LOOK).grouped(GROUPS::get);
        UiRoot root = root(select);
        root.focus().focus(select, true);
        root.keyPressed(key(InputConstants.KEY_RETURN));
        root.keyPressed(key(InputConstants.KEY_RIGHT));   // Item
        assertEquals(2, root.overlays().size());
        root.keyPressed(key(InputConstants.KEY_ESCAPE));
        assertEquals(1, root.overlays().size());
        assertTrue(select.isOpen());
        root.keyPressed(key(InputConstants.KEY_RIGHT));
        assertEquals(2, root.overlays().size());
        root.pointerPressed(399, 299, 0, 0, false);
        assertTrue(root.overlays().isEmpty());
        assertEquals("itemId", select.value());
    }

    @Test void theSubmenuSitsBesideTheListWithOverlappingGlows() {
        Select<String> select = Select.of(Signal.of("itemId"), () -> OPTIONS, Component::literal, LOOK).grouped(GROUPS::get);
        UiRoot root = root(select);
        root.focus().focus(select, true);
        root.keyPressed(key(InputConstants.KEY_RETURN));
        root.keyPressed(key(InputConstants.KEY_RIGHT));
        root.layoutIfNeeded();
        List<Widget> layers = new ArrayList<>(root.overlays().stream().map(UiRoot.Overlay::widget).toList());
        Rect list = ((Popup) layers.get(0)).children().getFirst().screenBounds();
        Rect sub = ((Popup) layers.get(1)).children().getFirst().screenBounds();
        assertEquals(list.right() - LOOK.popupOutset(), sub.x());
    }

    @Test void thePointerPathTowardsTheSubmenuIsATriangle() {
        Rect submenu = Rect.of(100, 40, 80, 60);
        assertTrue(SelectPopup.towardSubmenu(60, 50, 80, 60, submenu), "heading down-right into the submenu");
        assertTrue(SelectPopup.towardSubmenu(60, 50, 99, 98, submenu));
        assertFalse(SelectPopup.towardSubmenu(60, 50, 60, 80, submenu), "straight down to the next row");
        assertFalse(SelectPopup.towardSubmenu(60, 50, 40, 50, submenu), "away from the submenu");
        assertTrue(SelectPopup.towardSubmenu(160, 50, 130, 60, Rect.of(0, 40, 100, 60)), "a submenu on the left");
    }
}
