package com.ensox.ui.widget;

import com.ensox.base.Insets;
import com.ensox.ui.core.Container;
import com.ensox.ui.core.Widget;
import com.ensox.ui.event.KeyEvent;
import com.ensox.ui.event.PointerEvent;
import com.ensox.ui.event.UiEvent;
import com.ensox.ui.geom.Rect;
import com.ensox.ui.paint.Canvas;
import com.ensox.ui.paint.Paint;
import com.ensox.ui.paint.TextFit;
import com.ensox.ui.state.Signal;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Dropdown choice of one value from a list, bound to a {@code Signal<T>}.
 *
 * <ul>
 *     <li>Click, Enter or Space opens the list in a {@link Popup} below or above the field.</li>
 *     <li>A searchable Select with enough options turns its own field into the filter input while open;
 *     the list shows only the results, the matched parts lit, and a result count.</li>
 *     <li>Up/Down change the value without opening (when focused).</li>
 *     <li>Options are re-read on every open, so lists that change at runtime
 *     (resource reloads, user styles) stay current.</li>
 *     <li>Long labels are truncated, and scroll as a marquee while hovered.</li>
 *     <li>A grouped Select ({@link #grouped}) lists categories; each opens its options in a submenu beside
 *     the list. Filtering flattens the tree into "category › option" results, and the closed field shows
 *     the value's path the same way.</li>
 * </ul>
 */
public class Select<T> extends Container {
    /** Extra content per option, drawn on the right of the label (for example a texture preview). */
    @FunctionalInterface
    public interface OptionDecoration<T> {
        /** @return width taken from the right side of the content area, including any gap */
        int paint(Canvas canvas, T option, Rect content, boolean inField);
    }

    /** Visual configuration; build once per theme. */
    public record Look(
        Paint field,
        Paint fieldActive,
        Paint arrowClosed,
        Paint arrowOpen,
        Paint popupFrame,
        Insets popupPadding,
        Paint list,
        Insets listPadding,
        ScrollbarStyle scrollbar,
        int scrollbarThickness,
        int scrollbarCrossOffset,
        int scrollbarTrackStart,
        int scrollbarTrackEnd,
        int optionHeight,
        int maxVisibleOptions,
        int optionHoverFill,
        int textColor,
        int hoverTextColor,
        /** Magnifier painted in the field while it is the filter input. */
        Paint searchIcon,
        /** Matched parts of option labels while filtering. */
        int matchColor,
        /** Footer text and rule (escape hint, result count). */
        int hintColor,
        /** Left bar of the highlighted option. */
        int highlightBarColor,
        /**
         * How far the list reaches past the field on each side, for popup frames whose visible edge sits
         * inside the sprite (a glow around the rim), so the list looks exactly as wide as the field.
         */
        int popupOutset
    ) {
    }

    private static final int CONTENT_LEFT = 5;
    private static final int ARROW_RIGHT = 12;
    private static final int ARROW_WIDTH = 9;
    private static final int ARROW_HEIGHT = 8;
    private static final int ARROW_GAP = 4;
    private static final int SEARCH_ICON = 11;
    private static final int SEARCH_ICON_GAP = 3;

    private final Look look;
    private final Supplier<List<T>> options;
    private final Function<T, Component> label;
    private @Nullable OptionDecoration<T> decoration;
    private T value;
    private @Nullable java.util.function.Consumer<T> onChange;
    private @Nullable Supplier<Rect> visibleArea;
    private @Nullable Popup popup;
    private @Nullable Search search;
    /** The filter input; a child of this field only while the list is open with a search. */
    private @Nullable TextField query;
    private @Nullable SelectPopup<T> content;
    private Function<T, String> searchText = String::valueOf;
    private @Nullable Function<T, SelectGroup> grouping;
    private @Nullable Widget popupFocus;

    private record Search(int minimumOptions, Supplier<TextField> field, Component noResults) { }

    public Select(Look look, Supplier<List<T>> options, Function<T, Component> label) {
        this.look = look;
        this.options = options;
        this.label = label;
        focusable(true);
        style().cursor(CursorTypes.POINTING_HAND).size(142, 20);
    }

    public static <T> Select<T> of(Signal<T> value, Supplier<List<T>> options, Function<T, Component> label, Look look) {
        Select<T> select = new Select<>(look, options, label);
        select.bind(value, next -> select.value = next);
        select.onChange(value::set);
        return select;
    }

    public Select<T> decoration(@Nullable OptionDecoration<T> decoration) {
        this.decoration = decoration;
        return this;
    }

    public Select<T> onChange(java.util.function.Consumer<T> onChange) {
        this.onChange = onChange;
        return this;
    }

    /** Area the list must stay inside (for example the editor viewport); whole screen by default. */
    public Select<T> visibleArea(@Nullable Supplier<Rect> visibleArea) {
        this.visibleArea = visibleArea;
        return this;
    }

    /** Adds a fixed search field when there are at least {@code minimumOptions} choices. */
    public Select<T> searchable(int minimumOptions, Supplier<TextField> field, Component noResults) {
        if (minimumOptions < 1) throw new IllegalArgumentException("Search threshold must be positive");
        search = new Search(minimumOptions, Objects.requireNonNull(field), Objects.requireNonNull(noResults));
        return this;
    }

    /** Extra searchable text, alongside the displayed label; defaults to the option's string representation. */
    public Select<T> searchText(Function<T, String> searchText) {
        this.searchText = Objects.requireNonNull(searchText);
        return this;
    }

    /**
     * Groups the options into categories: {@code grouping} gives an option's category, or null for an option
     * shown in the list itself. Categories keep the order of their first options.
     */
    public Select<T> grouped(@Nullable Function<T, SelectGroup> grouping) {
        this.grouping = grouping;
        return this;
    }

    public T value() {
        return value;
    }

    public boolean isOpen() {
        return popup != null && popup.isOpen();
    }

    private void choose(T option) {
        if (!Objects.equals(value, option)) {
            value = option;
            if (onChange != null) {
                onChange.accept(option);
            }
        }
    }

    // ---- painting -------------------------------------------------------------------------------

    @Override
    protected void paint(Canvas canvas, Frame frame) {
        Rect b = bounds();
        boolean open = isOpen();
        (isHovered() || open || isFocusVisible() ? look.fieldActive() : look.field()).paint(canvas, b);
        int arrowX = b.width() - ARROW_RIGHT;
        if (query != null) {
            int iconY = b.y() + Math.floorDiv(b.height() - SEARCH_ICON, 2);
            look.searchIcon().paint(canvas, Rect.of(b.x() + CONTENT_LEFT, iconY, SEARCH_ICON, SEARCH_ICON));
        } else {
            Rect content = Rect.of(b.x() + CONTENT_LEFT, b.y(), arrowX - CONTENT_LEFT - ARROW_GAP, b.height());
            paintOption(canvas, value, groupOf(value), content, false, true, null);
        }
        int arrowY = b.y() + Math.floorDiv(b.height() - ARROW_HEIGHT, 2);
        (open ? look.arrowOpen() : look.arrowClosed()).paint(canvas, Rect.of(b.x() + arrowX, arrowY, ARROW_WIDTH, ARROW_HEIGHT));
    }

    private @Nullable SelectGroup groupOf(@Nullable T option) {
        return grouping == null || option == null ? null : grouping.apply(option);
    }

    /** Width of the "›" between a category and an option label, with its gaps. */
    private static final int PATH_SEPARATOR = 9;

    void paintOption(Canvas canvas, @Nullable T option, @Nullable SelectGroup prefix, Rect content, boolean hovered,
                     boolean inField, @Nullable Function<String, List<SelectOptions.Match>> matches) {
        if (option == null) {
            return;
        }
        if (prefix != null) {
            // The category is dim and at most half the width; the option label keeps the rest.
            String head = prefix.label().getString();
            int textY = content.y() + (content.height() - canvas.lineHeight()) / 2 + 1;
            int headWidth = Math.min(canvas.textWidth(head), content.width() / 2);
            TextFit.draw(canvas, head, content.x(), textY, headWidth, look.hintColor(), false, false);
            if (matches != null && canvas.textWidth(head) <= headWidth) {
                paintMatches(canvas, head, content.x(), textY, matches);
            }
            int chevronX = content.x() + headWidth + 3;
            for (int step = 0; step < 5; step++) {
                canvas.fill(Rect.of(chevronX + (step < 3 ? step : 4 - step), textY + 1 + step, 1, 1), look.hintColor());
            }
            int shift = headWidth + PATH_SEPARATOR;
            content = Rect.of(content.x() + shift, content.y(), Math.max(0, content.width() - shift), content.height());
        }
        int textRight = content.right() - 2;
        if (decoration != null) {
            int used = decoration.paint(canvas, option, content, inField);
            textRight = Math.min(textRight, content.right() - used);
        }
        String text = label.apply(option).getString();
        int textY = content.y() + (content.height() - canvas.lineHeight()) / 2 + 1;
        int width = textRight - content.x();
        boolean fits = canvas.textWidth(text) <= width;
        // A label that fits never scrolls, so the matched parts can be redrawn in place.
        TextFit.draw(canvas, text, content.x(), textY, width, hovered ? look.hoverTextColor() : look.textColor(), false,
            hovered && !fits);
        if (matches == null || !fits) {
            return;
        }
        paintMatches(canvas, text, content.x(), textY, matches);
    }

    private void paintMatches(Canvas canvas, String text, int left, int textY,
                              Function<String, List<SelectOptions.Match>> matches) {
        for (SelectOptions.Match match : matches.apply(text)) {
            int x = left + canvas.textWidth(text.substring(0, match.start()));
            String part = text.substring(match.start(), match.end());
            canvas.text(part, x, textY, look.matchColor(), false);
            canvas.fill(Rect.of(x, textY + canvas.lineHeight(), Math.max(1, canvas.textWidth(part) - 1), 1), look.matchColor());
        }
    }

    // ---- popup ------------------------------------------------------------------------------------

    private void toggleOpen() {
        if (isOpen()) {
            popup.close();
            return;
        }
        var root = root();
        if (root == null) return;
        List<T> items = new ArrayList<>(options.get());
        Rect area = visibleArea != null ? visibleArea.get() : Rect.of(0, 0, root.width(), root.height());
        int contentWidth = Math.max(1, Math.min(width() + 2 * look.popupOutset(), area.width()) - look.popupPadding().horizontal());
        Search activeSearch = search != null && items.size() >= search.minimumOptions() ? search : null;
        int footerHeight = activeSearch == null ? 0 : SelectPopup.footerHeight(look);
        Function<T, String> searchable = item -> {
            if (item == null) return "";
            SelectGroup group = groupOf(item);
            return (group == null ? "" : group.label().getString() + " ") + label.apply(item).getString() + " "
                + Objects.toString(searchText.apply(item), "");
        };
        SelectRows<T> rows = new SelectRows<>(new SelectOptions<>(items, value, searchable), grouping);
        int visibleRows = Math.max(1, Math.min(look.maxVisibleOptions(), Math.max(1, rows.rows().size())));
        int room = Math.max(area.bottom() - screenBounds().bottom(), screenBounds().y() - area.y());
        int extra = look.popupPadding().vertical() + look.listPadding().vertical() + footerHeight;
        visibleRows = Math.max(1, Math.min(visibleRows, (room - extra) / look.optionHeight()));
        int scrollHeight = visibleRows * look.optionHeight() + look.listPadding().vertical();
        SelectPopup<T> list = new SelectPopup<>(this, look, rows, activeSearch != null,
            activeSearch == null ? Component.empty() : activeSearch.noResults(), contentWidth, scrollHeight, option -> {
                // Restore the anchor before callbacks can replace its widget subtree.
                closePopup();
                choose(option);
            }, null);
        list.style().margin(Insets.of(0, -look.popupOutset(), 0, -look.popupOutset()));
        content = list;
        boolean keyboardFocus = isFocusVisible();
        popup = Popup.open(this, list, visibleArea, () -> {
            popup = null;
            content = null;
            popupFocus = null;
            closeQuery();
            if (root() == root && root.focus().focused() == null) root.focus().focus(this, keyboardFocus);
        });
        Widget initialFocus = list;
        if (activeSearch != null) {
            TextField field = activeSearch.field().get();
            // The field paints the frame and the magnifier; the input is only text and caret.
            field.background(Paint.NONE, Paint.NONE).text("");
            field.onChange(list::filter);
            query = field;
            add(field);
            popup.keepOpenOn(field);
            initialFocus = field;
        }
        popupFocus = initialFocus;
        root.layoutIfNeeded();
        list.revealHighlighted();
        root.focus().focus(initialFocus, keyboardFocus);
    }

    /** The area popups of this Select stay inside; null for the whole screen. */
    @Nullable Supplier<Rect> popupArea() {
        return visibleArea;
    }

    void closePopup() {
        if (popup != null) {
            popup.close();
        }
    }

    /** Keyboard back to the open list (or its filter input), after a submenu closed. */
    void refocusPopup() {
        var root = root();
        if (root != null && popupFocus != null && popupFocus.root() == root) {
            root.focus().focus(popupFocus, true);
        }
    }

    private void closeQuery() {
        if (query != null) {
            TextField field = query;
            query = null;
            remove(field);
        }
    }

    // ---- layout of the filter input ---------------------------------------------------------------

    @Override
    protected void measureSelf(int availableWidth, int availableHeight, int spaceWidth, int spaceHeight) {
        super.measureSelf(availableWidth, availableHeight, spaceWidth, spaceHeight);
        if (query != null) {
            int left = CONTENT_LEFT + SEARCH_ICON + SEARCH_ICON_GAP;
            // The input centres its text in the content box: one pixel off the top puts it on the label's line.
            query.style().size(Math.max(1, width() - left - ARROW_RIGHT - ARROW_GAP), height())
                .padding(Insets.of(1, 0, 0, 0)).margin(Insets.of(0));
            query.measure(width(), height());
        }
    }

    @Override
    protected void arrangeChildren() {
        if (query != null) {
            query.arrange(x() + CONTENT_LEFT + SEARCH_ICON + SEARCH_ICON_GAP, y());
        }
    }

    /** While filtering, the list keys reach the open list from the input in this field. */
    @Override
    protected void intercept(UiEvent event) {
        if (query != null && content != null && event instanceof KeyEvent key && key.target() == query
            && content.handleKey(key, true)) {
            key.consume();
        }
    }

    // ---- input ---------------------------------------------------------------------------------------

    @Override
    protected void onPointer(PointerEvent event) {
        if (event.type() == PointerEvent.Type.PRESS && event.isPrimary()) {
            toggleOpen();
            event.consume();
        }
    }

    @Override
    protected void onKey(KeyEvent event) {
        if (event.type() != KeyEvent.Type.PRESS || !isFocused()) {
            return;
        }
        switch (event.key()) {
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_SPACE -> toggleOpen();
            case InputConstants.KEY_UP, InputConstants.KEY_DOWN -> {
                List<T> items = options.get();
                if (items.isEmpty()) {
                    return;
                }
                int index = items.indexOf(value);
                int next = Math.floorMod(index + (event.key() == InputConstants.KEY_DOWN ? 1 : -1), items.size());
                choose(items.get(next));
            }
            default -> {
                return;
            }
        }
        event.consume();
    }

    @Override
    protected void onDetach() {
        if (popup != null) {
            popup.close();
        }
    }
}
