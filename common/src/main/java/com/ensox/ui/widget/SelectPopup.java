package com.ensox.ui.widget;

import com.ensox.ui.core.Column;
import com.ensox.ui.core.UiRoot;
import com.ensox.ui.core.Widget;
import com.ensox.ui.event.KeyEvent;
import com.ensox.ui.event.UiEvent;
import com.ensox.ui.geom.Rect;
import com.ensox.ui.layout.Sizing;
import com.ensox.ui.paint.Canvas;
import com.ensox.ui.paint.TextFit;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * The open list of one Select: scrolling results and, while filtering, a footer with the result count.
 * The filter text itself is typed into the Select field (see {@link Select}), so the list holds only results.
 *
 * <p>A grouped Select shows its categories here; hovering a category, or Right/Enter on it, opens its options
 * in a submenu beside the list (another SelectPopup in its own overlay). While the pointer travels from the open
 * category towards the submenu, rows it crosses do not switch the submenu for a short grace period.</p>
 */
final class SelectPopup<T> extends Column {
    /** Escape closes the list; the footer says so next to the count. */
    private static final String ESCAPE_HINT = "Esc";
    /** How long the pointer may cross other rows on its way into the open submenu. */
    static final long SUBMENU_GRACE_NANOS = 300_000_000L;

    private final Select<T> owner;
    private final Select.Look look;
    private final SelectRows<T> rows;
    private final boolean filtered;
    private final Component noResults;
    private final Consumer<T> choose;
    private final int contentWidth;
    private final Column list = new Column();
    /** Category rows by list index (inner classes of a generic list cannot be matched with instanceof). */
    private final java.util.Map<Integer, GroupRow> groupRows = new java.util.HashMap<>();
    private final ScrollView scroll;
    /** The list whose category opened this one, or null for the Select's own list. */
    private final @Nullable SelectPopup<T> parent;

    private record Submenu<T>(Widget row, Popup popup, SelectPopup<T> list) { }

    private @Nullable Submenu<T> submenu;
    private boolean closingSubmenu;
    private double pointerX;
    private double pointerY;
    private long crossingSince = -1;
    /** Ends the frame task of an earlier attachment. */
    private int attachments;

    SelectPopup(Select<T> owner, Select.Look look, SelectOptions<T> options, boolean filtered,
                Component noResults, int contentWidth, int scrollHeight, Consumer<T> choose) {
        this(owner, look, new SelectRows<>(options, null), filtered, noResults, contentWidth, scrollHeight, choose, null);
    }

    SelectPopup(Select<T> owner, Select.Look look, SelectRows<T> rows, boolean filtered, Component noResults,
                int contentWidth, int scrollHeight, Consumer<T> choose, @Nullable SelectPopup<T> parent) {
        this.owner = owner;
        this.look = look;
        this.rows = rows;
        this.filtered = filtered;
        this.noResults = noResults;
        this.choose = choose;
        this.contentWidth = contentWidth;
        this.parent = parent;
        focusable(true);
        style().padding(look.popupPadding()).background(look.popupFrame()).overflow();
        list.style().width(Sizing.grow());
        scroll = ScrollView.vertical(list)
            .scrollbar(look.scrollbar()).scrollbarThickness(look.scrollbarThickness())
            .scrollbarOffsets(look.scrollbarCrossOffset(), look.scrollbarTrackStart(), look.scrollbarTrackEnd());
        scroll.focusable(false);
        scroll.style().size(contentWidth, scrollHeight).padding(look.listPadding()).background(look.list());
        add(scroll);
        if (filtered) add(new Footer(contentWidth));
        rebuildRows();
    }

    /** Height of the footer row shown while filtering. */
    static int footerHeight(Select.Look look) {
        return look.optionHeight() - 4;
    }

    /** Height of the scrolling list for {@code count} rows, at most the Look's visible maximum. */
    static int listHeight(Select.Look look, int count) {
        return Math.max(1, Math.min(look.maxVisibleOptions(), count)) * look.optionHeight() + look.listPadding().vertical();
    }

    void filter(String query) {
        closeSubmenu(false);
        rows.filter(query);
        rebuildRows();
        // Recompute the scroll range before revealing the new highlight.
        if (root() != null) root().layoutIfNeeded();
        revealHighlighted();
    }

    private void rebuildRows() {
        list.clear();
        groupRows.clear();
        var items = rows.rows();
        for (int i = 0; i < items.size(); i++) {
            list.add(switch (items.get(i)) {
                case SelectRows.Option<T> option -> new OptionRow(option.value(), option.prefix(), option.ruleAbove(), i);
                case SelectRows.Group<T> group -> {
                    GroupRow row = new GroupRow(group, i);
                    groupRows.put(i, row);
                    yield row;
                }
            });
        }
        if (items.isEmpty()) list.add(new EmptyRow());
    }

    void revealHighlighted() {
        if (rows.highlightedIndex() < 0) {
            scroll.scrollTo(0, 0, false);
            return;
        }
        int top = rows.highlightedIndex() * look.optionHeight();
        int bottom = top + look.optionHeight();
        int viewport = Math.max(0, scroll.height() - look.listPadding().vertical());
        double offset = scroll.scrollY();
        if (top < offset) offset = top;
        else if (bottom > offset + viewport) offset = bottom - viewport;
        scroll.scrollTo(0, offset, false);
    }

    @Override
    protected void intercept(UiEvent event) {
        if (event instanceof KeyEvent key && handleKey(key, false)) key.consume();
    }

    /**
     * List keys: arrows move the highlight, Enter accepts it; Space accepts too unless the user is typing a
     * filter, where it belongs to the query. Right (or Enter) on a category opens its submenu; Left or Escape
     * in a submenu returns to the list that opened it.
     */
    boolean handleKey(KeyEvent key, boolean typing) {
        if (key.type() != KeyEvent.Type.PRESS) return false;
        switch (key.key()) {
            case InputConstants.KEY_UP, InputConstants.KEY_DOWN -> {
                rows.move(key.key() == InputConstants.KEY_DOWN ? 1 : -1);
                revealHighlighted();
            }
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> acceptHighlighted();
            case InputConstants.KEY_SPACE -> {
                if (typing) return false;
                acceptHighlighted();
            }
            case InputConstants.KEY_RIGHT -> {
                if (!(rows.highlighted() instanceof SelectRows.Group<T>)) return false;
                acceptHighlighted();
            }
            case InputConstants.KEY_LEFT -> {
                if (parent == null) return false;
                parent.closeSubmenu(true);
            }
            case InputConstants.KEY_ESCAPE -> {
                if (parent != null) parent.closeSubmenu(true);
                else if (submenu != null) closeSubmenu(true);
                else return false;
            }
            default -> { return false; }
        }
        return true;
    }

    private void acceptHighlighted() {
        int index = rows.highlightedIndex();
        SelectRows.Row<T> row = rows.highlighted();
        if (row instanceof SelectRows.Option<T> option) {
            choose.accept(option.value());
        } else if (row instanceof SelectRows.Group<T> && groupRows.containsKey(index)) {
            openSubmenu(groupRows.get(index), true);
        }
    }

    // ---- submenus ----------------------------------------------------------------------------------

    private void openSubmenu(GroupRow row, boolean keyboard) {
        if (submenu != null && submenu.row() == row) {
            if (keyboard) focusSubmenu();
            return;
        }
        closeSubmenu(false);
        if (root() == null) return;
        SelectPopup<T> content = new SelectPopup<>(owner, look, SelectRows.submenu(row.group, owner.value()), false,
            noResults, contentWidth, listHeight(look, row.group.children().size()), choose, this);
        // The submenu's first row lines up with the category row; the frames' glows overlap.
        int top = -(look.popupPadding().top() + look.listPadding().top());
        Popup popup = Popup.openBeside(row, this, content, owner.popupArea(), () -> submenuClosed(content),
            look.popupOutset(), top);
        // Clicks on any list of the chain switch submenus there instead of closing everything.
        for (SelectPopup<T> list = this; list != null; list = list.parent) popup.keepOpenOn(list);
        popup.keepOpenOn(owner);
        submenu = new Submenu<>(row, popup, content);
        rows.highlight(row.index);
        if (keyboard) focusSubmenu();
    }

    private void focusSubmenu() {
        UiRoot root = root();
        if (root == null || submenu == null) return;
        root.layoutIfNeeded();
        submenu.list().revealHighlighted();
        root.focus().focus(submenu.list(), true);
    }

    /** Closes the open submenu, if any; {@code refocus} hands the keyboard back to this list. */
    void closeSubmenu(boolean refocus) {
        Submenu<T> open = submenu;
        if (open == null) return;
        submenu = null;
        closingSubmenu = true;
        try {
            open.popup().close();
        } finally {
            closingSubmenu = false;
        }
        crossingSince = -1;
        if (!refocus) return;
        UiRoot root = root();
        if (parent != null && root != null) root.focus().focus(this, true);
        else owner.refocusPopup();
    }

    /** A submenu closed on its own (a click outside every list): the whole Select list closes. */
    private void submenuClosed(SelectPopup<T> content) {
        if (closingSubmenu || submenu == null || submenu.list() != content) return;
        submenu = null;
        owner.closePopup();
    }

    @Nullable SelectPopup<T> submenuList() {
        return submenu == null ? null : submenu.list();
    }

    @Override
    protected void onAttach() {
        // Hover intent runs at the start of a frame, before layout and rendering: it opens and closes
        // overlays, which must not happen while the root walks its layers.
        int generation = ++attachments;
        UiRoot root = root();
        if (root != null) root.animations().add(delta -> {
            if (generation != attachments || root() != root) return false;
            if (rows.isTree()) followPointer(System.nanoTime());
            return true;
        });
    }

    @Override
    protected void onDetach() {
        attachments++;
        closeSubmenu(false);
    }

    /** Hover switches the submenu, except while the pointer crosses rows on its way into the open one. */
    void followPointer(long now) {
        UiRoot root = root();
        if (root == null) return;
        Widget hovered = null;
        int hoveredIndex = -1;
        for (int i = 0; i < list.children().size(); i++) {
            if (list.children().get(i).isHovered()) {
                hovered = list.children().get(i);
                hoveredIndex = i;
            }
        }
        if (hovered == null) {
            crossingSince = -1;
            return;
        }
        double x = root.mouseX();
        double y = root.mouseY();
        if (submenu != null && hovered == submenu.row()) {
            pointerX = x;
            pointerY = y;
            crossingSince = -1;
            return;
        }
        if (submenu != null && towardSubmenu(pointerX, pointerY, x, y, submenu.list().screenBounds())) {
            if (crossingSince < 0) crossingSince = now;
            if (now - crossingSince < SUBMENU_GRACE_NANOS) return;
        }
        crossingSince = -1;
        GroupRow row = groupRows.get(hoveredIndex);
        if (row != null) {
            openSubmenu(row, false);
            pointerX = x;
            pointerY = y;
        } else {
            closeSubmenu(false);
        }
    }

    /**
     * Whether {@code (x, y)} lies in the triangle from the last pointer position on the open category,
     * {@code (fromX, fromY)}, to the submenu's near edge: the path of a pointer heading into the submenu.
     */
    static boolean towardSubmenu(double fromX, double fromY, double x, double y, Rect submenu) {
        double edge = submenu.x() >= fromX ? submenu.x() : submenu.right();
        double topY = submenu.y() - 1;
        double bottomY = submenu.bottom() + 1;
        double d1 = cross(x, y, fromX, fromY, edge, topY);
        double d2 = cross(x, y, edge, topY, edge, bottomY);
        double d3 = cross(x, y, edge, bottomY, fromX, fromY);
        boolean negative = d1 < 0 || d2 < 0 || d3 < 0;
        boolean positive = d1 > 0 || d2 > 0 || d3 > 0;
        return !(negative && positive);
    }

    private static double cross(double px, double py, double ax, double ay, double bx, double by) {
        return (px - bx) * (ay - by) - (ax - bx) * (py - by);
    }

    // ---- rows ------------------------------------------------------------------------------------------

    /**
     * The highlighted row (the value, the keyboard's choice, an open category) has the bar; the hovered one
     * only a paler band, so the two never look alike.
     */
    private void paintBand(Canvas canvas, Rect b, boolean selected, boolean hovered) {
        if (selected) {
            canvas.fill(Rect.of(b.x() + 1, b.y(), b.width() - 2, b.height()), look.optionHoverFill());
            canvas.fill(Rect.of(b.x() + 1, b.y(), 2, b.height()), look.highlightBarColor());
        } else if (hovered) {
            int fill = look.optionHoverFill();
            canvas.fill(Rect.of(b.x() + 1, b.y(), b.width() - 2, b.height()), ((fill >>> 24) / 2) << 24 | (fill & 0xFFFFFF));
        }
    }

    private void paintRule(Canvas canvas, Rect b) {
        for (int x = b.x() + 2; x < b.right() - 2; x += 2) canvas.fill(Rect.of(x, b.y(), 1, 1), look.hintColor());
    }

    private final class OptionRow extends Widget {
        private final T option;
        private final @Nullable SelectGroup prefix;
        private final boolean ruleAbove;
        private final int index;

        OptionRow(T option, @Nullable SelectGroup prefix, boolean ruleAbove, int index) {
            this.option = option;
            this.prefix = prefix;
            this.ruleAbove = ruleAbove;
            this.index = index;
            style().size(Sizing.grow(), Sizing.fixed(look.optionHeight())).cursor(CursorTypes.POINTING_HAND);
            onClick(() -> choose.accept(option));
        }

        @Override
        protected void paint(Canvas canvas, Frame frame) {
            Rect b = bounds();
            boolean selected = index == rows.highlightedIndex();
            paintBand(canvas, b, selected, isHovered());
            if (ruleAbove) paintRule(canvas, b);
            owner.paintOption(canvas, option, prefix, Rect.of(b.x() + 6, b.y(), b.width() - 11, b.height()),
                selected || isHovered(), false, filtered ? rows.options()::matches : null);
        }
    }

    /** A category: its label and a right-pointing arrow; it opens its submenu on hover, click or Right. */
    private final class GroupRow extends Widget {
        private static final int ARROW_WIDTH = 4;
        private static final int ARROW_HEIGHT = 7;
        private final SelectRows.Group<T> group;
        private final int index;

        GroupRow(SelectRows.Group<T> group, int index) {
            this.group = group;
            this.index = index;
            style().size(Sizing.grow(), Sizing.fixed(look.optionHeight())).cursor(CursorTypes.POINTING_HAND);
            onClick(() -> openSubmenu(this, false));
        }

        @Override
        protected void paint(Canvas canvas, Frame frame) {
            Rect b = bounds();
            boolean open = submenu != null && submenu.row() == this;
            boolean selected = open || index == rows.highlightedIndex();
            paintBand(canvas, b, selected, isHovered());
            if (group.group().ruleAbove() && index > 0) paintRule(canvas, b);
            int arrowX = b.right() - 6 - ARROW_WIDTH;
            int textY = b.y() + (b.height() - canvas.lineHeight()) / 2 + 1;
            TextFit.draw(canvas, group.group().label().getString(), b.x() + 6, textY, Math.max(0, arrowX - 4 - b.x() - 6),
                selected || isHovered() ? look.hoverTextColor() : look.textColor(), false, false);
            int arrowY = b.y() + (b.height() - ARROW_HEIGHT) / 2;
            int color = open ? look.highlightBarColor() : look.textColor();
            for (int column = 0; column < ARROW_WIDTH; column++) {
                canvas.fill(Rect.of(arrowX + column, arrowY + column, 1, ARROW_HEIGHT - 2 * column), color);
            }
        }
    }

    private final class EmptyRow extends Widget {
        EmptyRow() { style().size(Sizing.grow(), Sizing.fixed(look.optionHeight())); }

        @Override
        protected void paint(Canvas canvas, Frame frame) {
            TextFit.draw(canvas, noResults.getString(), x() + 6, y() + (height() - canvas.lineHeight()) / 2 + 1,
                Math.max(0, width() - 11), look.textColor(), false, false);
        }
    }

    /** "Esc" on the left and "visible/total" on the right, under a dotted rule. */
    private final class Footer extends Widget {
        Footer(int width) {
            style().size(width, footerHeight(look));
        }

        @Override
        protected void paint(Canvas canvas, Frame frame) {
            Rect b = bounds();
            for (int x = b.x() + 1; x < b.right() - 1; x += 2) canvas.fill(Rect.of(x, b.y(), 1, 1), look.hintColor());
            int textY = b.y() + (b.height() - canvas.lineHeight()) / 2 + 2;
            canvas.text(ESCAPE_HINT, b.x() + 4, textY, look.hintColor(), false);
            String count = rows.options().visibleCount() + "/" + rows.options().totalCount();
            canvas.text(count, b.right() - 3 - canvas.textWidth(count), textY, look.hintColor(), false);
        }
    }
}
