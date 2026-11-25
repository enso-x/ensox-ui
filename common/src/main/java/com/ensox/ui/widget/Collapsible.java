package com.ensox.ui.widget;

import com.ensox.base.Easing;
import com.ensox.ui.anim.Animated;
import com.ensox.ui.core.Column;
import com.ensox.ui.core.Row;
import com.ensox.ui.core.Widget;
import com.ensox.ui.geom.Rect;
import com.ensox.ui.layout.Align;
import com.ensox.ui.layout.Sizing;
import com.ensox.ui.paint.Canvas;
import com.ensox.ui.paint.Paint;
import com.ensox.ui.state.Signal;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Section with a clickable header that expands and collapses its content with
 * an animated height. Expansion state lives in a {@code Signal<Boolean>}, so
 * callers can keep it across rebuilds.
 */
public class Collapsible extends Column {
    private static final int ANIMATION_MS = 200;
    private static final int ARROW_WIDTH = 9;
    private static final int ARROW_HEIGHT = 8;

    private final Signal<Boolean> expanded;
    private final Label title;
    private final Column clip;
    private final Animated<Sizing> height;

    /**
     * @param arrowCollapsed / arrowExpanded marker paints (9x8)
     * @param trailing       optional control at the right of the header (for example a delete icon)
     */
    public Collapsible(Component titleText, Widget content, Signal<Boolean> expanded,
                       Paint arrowCollapsed, Paint arrowExpanded, @Nullable Widget trailing) {
        this.expanded = expanded;
        this.title = Label.of(titleText);

        Widget arrow = new Widget() {
            @Override
            protected int contentWidth() {
                return ARROW_WIDTH;
            }

            @Override
            protected int contentHeight(int contentWidth) {
                return ARROW_HEIGHT;
            }

            @Override
            protected void paint(Canvas canvas, Frame frame) {
                (Collapsible.this.expanded.get() ? arrowExpanded : arrowCollapsed).paint(canvas, Rect.of(x(), y(), ARROW_WIDTH, ARROW_HEIGHT));
            }
        };

        Row header = Row.of(arrow, title, new Spacer());
        if (trailing != null) {
            header.add(trailing);
        }
        header.style(s -> s.size(Sizing.fill(), Sizing.fixed(22)).gap(8).alignY(Align.CENTER).cursor(CursorTypes.POINTING_HAND));
        header.onClick(() -> this.expanded.set(!this.expanded.get()));

        this.clip = Column.of(content);
        clip.style(s -> s.width(Sizing.fill()));
        this.height = Animated.of(clip, expanded.get() ? Sizing.content() : Sizing.fixed(0), v -> clip.style().height(v));

        add(header, clip);
        style(s -> s.width(Sizing.fill()));
        bind(expanded, open -> height.animateTo(open ? Sizing.content() : Sizing.fixed(0), ANIMATION_MS, Easing.EASE_IN_OUT));
    }

    public Collapsible title(Component text) {
        title.text(text);
        return this;
    }

    public boolean isExpanded() {
        return expanded.get();
    }
}
