package com.ensox.ui.widget;

import com.ensox.ui.core.Column;
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

import java.util.function.Consumer;

/**
 * The open list of one Select: scrolling results and, while filtering, a footer with the result count.
 * The filter text itself is typed into the Select field (see {@link Select}), so the list holds only results.
 */
final class SelectPopup<T> extends Column {
    /** Escape closes the list; the footer says so next to the count. */
    private static final String ESCAPE_HINT = "Esc";

    private final Select<T> owner;
    private final Select.Look look;
    private final SelectOptions<T> options;
    private final boolean filtered;
    private final Component noResults;
    private final Consumer<T> choose;
    private final Column list = new Column();
    private final ScrollView scroll;

    SelectPopup(Select<T> owner, Select.Look look, SelectOptions<T> options, boolean filtered,
                Component noResults, int contentWidth, int scrollHeight, Consumer<T> choose) {
        this.owner = owner;
        this.look = look;
        this.options = options;
        this.filtered = filtered;
        this.noResults = noResults;
        this.choose = choose;
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

    void filter(String query) {
        options.filter(query);
        rebuildRows();
        // Recompute the scroll range before revealing the new highlight.
        if (root() != null) root().layoutIfNeeded();
        revealHighlighted();
    }

    private void rebuildRows() {
        list.clear();
        var items = options.visible();
        for (int i = 0; i < items.size(); i++) list.add(new OptionRow(items.get(i), i));
        if (items.isEmpty()) list.add(new EmptyRow());
    }

    void revealHighlighted() {
        if (options.highlightedIndex() < 0) {
            scroll.scrollTo(0, 0, false);
            return;
        }
        int top = options.highlightedIndex() * look.optionHeight();
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
     * filter, where it belongs to the query.
     */
    boolean handleKey(KeyEvent key, boolean typing) {
        if (key.type() != KeyEvent.Type.PRESS) return false;
        switch (key.key()) {
            case InputConstants.KEY_UP, InputConstants.KEY_DOWN -> {
                options.move(key.key() == InputConstants.KEY_DOWN ? 1 : -1);
                revealHighlighted();
            }
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> acceptHighlighted();
            case InputConstants.KEY_SPACE -> {
                if (typing) return false;
                acceptHighlighted();
            }
            default -> { return false; }
        }
        return true;
    }

    private void acceptHighlighted() {
        if (options.highlightedIndex() >= 0) choose.accept(options.highlighted());
    }

    private final class OptionRow extends Widget {
        private final T option;
        private final int index;

        OptionRow(T option, int index) {
            this.option = option;
            this.index = index;
            style().size(Sizing.grow(), Sizing.fixed(look.optionHeight())).cursor(CursorTypes.POINTING_HAND);
            onClick(() -> choose.accept(option));
        }

        @Override
        protected void paint(Canvas canvas, Frame frame) {
            Rect b = bounds();
            // The highlighted option (the value, or the keyboard's choice) has the bar; the hovered one only a
            // paler band, so the two never look alike.
            boolean selected = index == options.highlightedIndex();
            boolean highlighted = selected || isHovered();
            if (selected) {
                canvas.fill(Rect.of(b.x() + 1, b.y(), b.width() - 2, b.height()), look.optionHoverFill());
                canvas.fill(Rect.of(b.x() + 1, b.y(), 2, b.height()), look.highlightBarColor());
            } else if (highlighted) {
                int fill = look.optionHoverFill();
                canvas.fill(Rect.of(b.x() + 1, b.y(), b.width() - 2, b.height()), ((fill >>> 24) / 2) << 24 | (fill & 0xFFFFFF));
            }
            owner.paintOption(canvas, option, Rect.of(b.x() + 6, b.y(), b.width() - 11, b.height()), highlighted, false,
                filtered ? options::matches : null);
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
            String count = options.visibleCount() + "/" + options.totalCount();
            canvas.text(count, b.right() - 3 - canvas.textWidth(count), textY, look.hintColor(), false);
        }
    }
}
