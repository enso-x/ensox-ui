package com.ensox.ui.widget;

import com.ensox.base.Insets;
import com.ensox.ui.core.Column;
import com.ensox.ui.core.Widget;
import com.ensox.ui.event.KeyEvent;
import com.ensox.ui.event.PointerEvent;
import com.ensox.ui.geom.Rect;
import com.ensox.ui.layout.Sizing;
import com.ensox.ui.paint.Canvas;
import com.ensox.ui.paint.Paint;
import com.ensox.ui.paint.TextFit;
import com.ensox.ui.state.Signal;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Dropdown choice of one value from a list, bound to a {@code Signal<T>}.
 *
 * <ul>
 *     <li>Click, Enter or Space opens the list in a {@link Popup} below or above the field.</li>
 *     <li>Up/Down change the value without opening (when focused).</li>
 *     <li>Options are re-read on every open, so lists that change at runtime
 *     (resource reloads, user presets) stay current.</li>
 *     <li>Long labels are truncated, and scroll as a marquee while hovered.</li>
 * </ul>
 */
public class Select<T> extends Widget {
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
        int hoverTextColor
    ) {
    }

    private static final int CONTENT_LEFT = 5;
    private static final int ARROW_RIGHT = 12;
    private static final int ARROW_WIDTH = 9;
    private static final int ARROW_HEIGHT = 8;
    private static final int ARROW_GAP = 4;

    private final Look look;
    private final Supplier<List<T>> options;
    private final Function<T, Component> label;
    private @Nullable OptionDecoration<T> decoration;
    private T value;
    private @Nullable java.util.function.Consumer<T> onChange;
    private @Nullable Supplier<Rect> visibleArea;
    private @Nullable Popup popup;

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
        Rect content = Rect.of(b.x() + CONTENT_LEFT, b.y(), arrowX - CONTENT_LEFT - ARROW_GAP, b.height());
        paintOption(canvas, value, content, false, true);
        (open ? look.arrowOpen() : look.arrowClosed()).paint(canvas, Rect.of(b.x() + arrowX, b.y() + 5, ARROW_WIDTH, ARROW_HEIGHT));
    }

    void paintOption(Canvas canvas, @Nullable T option, Rect content, boolean hovered, boolean inField) {
        if (option == null) {
            return;
        }
        int textRight = content.right() - 2;
        if (decoration != null) {
            int used = decoration.paint(canvas, option, content, inField);
            textRight = Math.min(textRight, content.right() - used);
        }
        int textY = content.y() + (content.height() - canvas.lineHeight()) / 2 + 1;
        TextFit.draw(canvas, label.apply(option).getString(), content.x(), textY, textRight - content.x(),
            hovered ? look.hoverTextColor() : look.textColor(), false, hovered);
    }

    // ---- popup ------------------------------------------------------------------------------------

    private void toggleOpen() {
        if (isOpen()) {
            popup.close();
            return;
        }
        List<T> items = options.get();
        Rect area = visibleArea != null ? visibleArea.get() : null;
        int visibleRows = Math.max(1, Math.min(look.maxVisibleOptions(), Math.max(1, items.size())));
        int scrollHeight = visibleRows * look.optionHeight() + 6;
        int frameExtra = look.popupPadding().vertical();
        if (area != null) {
            int roomDown = area.bottom() - bounds().bottom();
            int roomUp = y() - area.y();
            if (scrollHeight + frameExtra > roomDown && scrollHeight + frameExtra > roomUp) {
                int room = Math.max(roomDown, roomUp);
                int rows = Math.max(1, Math.min(visibleRows, (room - 4) / look.optionHeight()));
                scrollHeight = rows * look.optionHeight() + 6;
            }
        }

        Column list = new Column();
        list.style(s -> s.width(Sizing.grow()));
        for (T item : items) {
            list.add(new OptionRow(item));
        }
        ScrollView scroll = ScrollView.vertical(list)
            .scrollbar(look.scrollbar())
            .scrollbarThickness(look.scrollbarThickness())
            .scrollbarOffsets(look.scrollbarCrossOffset(), look.scrollbarTrackStart(), look.scrollbarTrackEnd());
        int scrollHeightFinal = scrollHeight;
        scroll.style(s -> s.size(width() - 10, scrollHeightFinal).padding(look.listPadding()).background(look.list()));
        Column frame = Column.of(scroll);
        frame.style(s -> s.padding(look.popupPadding()).background(look.popupFrame()).overflow());
        popup = Popup.open(this, frame, visibleArea, () -> popup = null);
    }

    private final class OptionRow extends Widget {
        private final T option;

        OptionRow(T option) {
            this.option = option;
            style().size(Sizing.grow(), Sizing.fixed(look.optionHeight())).cursor(CursorTypes.POINTING_HAND);
            onClick(() -> {
                choose(option);
                if (popup != null) {
                    popup.close();
                }
            });
        }

        @Override
        protected void paint(Canvas canvas, Frame frame) {
            Rect b = bounds();
            if (isHovered()) {
                canvas.fill(Rect.of(b.x() + 1, b.y(), b.width() - 2, b.height()), look.optionHoverFill());
            }
            paintOption(canvas, option, Rect.of(b.x() + 5, b.y(), b.width() - 10, b.height()), isHovered(), false);
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
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> toggleOpen();
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                List<T> items = options.get();
                if (items.isEmpty()) {
                    return;
                }
                int index = items.indexOf(value);
                int next = Math.floorMod(index + (event.key() == GLFW.GLFW_KEY_DOWN ? 1 : -1), items.size());
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
