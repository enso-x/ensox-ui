package com.ensox.ui.core;

import com.ensox.base.Insets;
import com.ensox.ui.geom.Rect;
import com.ensox.ui.layout.Position;
import com.ensox.ui.paint.Canvas;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Widget with children. Handles everything children have in common: the
 * tree, placement of children outside the flow ({@code absolute}, {@code anchor},
 * {@code fraction}), clipping, drawing and hit testing. Subclasses implement
 * the actual arrangement ({@link Flow}, {@link com.ensox.ui.widget.ScrollView}).
 */
public abstract class Container extends Widget {
    private final List<Widget> children = new ArrayList<>();
    private final List<Widget> childrenView = Collections.unmodifiableList(children);

    @Override
    public List<Widget> children() {
        return childrenView;
    }

    public Container add(Widget... widgets) {
        for (Widget widget : widgets) {
            insert(children.size(), widget);
        }
        return this;
    }

    /**
     * Appends a list of children. Deliberately not an {@code add} overload:
     * {@code Widget.style(...)} returns an inferred type, and with two
     * {@code add} overloads {@code add(widget.style(...))} could resolve to the
     * list variant and fail at runtime with a ClassCastException.
     */
    public Container addAll(List<? extends Widget> widgets) {
        for (Widget widget : widgets) {
            insert(children.size(), widget);
        }
        return this;
    }

    public Container insert(int index, Widget widget) {
        if (widget.parent() != null) {
            throw new IllegalStateException("Widget already has a parent: " + widget);
        }
        children.add(index, widget);
        widget.setParent(this);
        if (root() != null) {
            widget.attach(root());
        }
        invalidateLayout();
        return this;
    }

    public Container remove(Widget widget) {
        if (children.remove(widget)) {
            release(widget);
            invalidateLayout();
        }
        return this;
    }

    public Container clear() {
        for (Widget child : children) {
            release(child);
        }
        children.clear();
        invalidateLayout();
        return this;
    }

    /** Replaces all children, for content rebuilt from data. */
    public Container replaceChildren(List<? extends Widget> widgets) {
        clear();
        return addAll(widgets);
    }

    private void release(Widget child) {
        child.detach();
        child.setParent(null);
    }

    // ---- layout helpers for subclasses ---------------------------------------------

    /** Visible children; invisible ones take no space. */
    protected final List<Widget> visibleChildren() {
        List<Widget> visible = new ArrayList<>(children.size());
        for (Widget child : children) {
            if (child.style().visible()) {
                visible.add(child);
            }
        }
        return visible;
    }

    /**
     * Space offered to children on one axis. Content-sized axes offer the
     * space this container was offered; other axes offer its own size.
     */
    protected static int childSpace(boolean contentSized, int offeredSpace, int ownSize, int paddingSum) {
        return (contentSized ? offeredSpace : ownSize) - paddingSum;
    }

    /** Positions a child that is not part of the flow, relative to the content box. */
    protected final void arrangeOutOfFlow(Widget child) {
        Position position = child.style().position();
        Insets margin = child.style().margin();
        Insets padding = style().padding();
        int left = x() + padding.left() + margin.left();
        int top = y() + padding.top() + margin.top();
        int innerWidth = width() - padding.horizontal();
        int innerHeight = height() - padding.vertical();
        switch (position.kind()) {
            case ABSOLUTE -> child.arrange(left + position.x(), top + position.y());
            case ANCHOR -> child.arrange(
                left + Math.round((position.x() / 100f) * (innerWidth - child.outerWidth())),
                top + Math.round((position.y() / 100f) * (innerHeight - child.outerHeight())));
            case FRACTION -> child.arrange(
                left + Math.round((position.x() / 100f) * innerWidth),
                top + Math.round((position.y() / 100f) * innerHeight));
            default -> throw new IllegalArgumentException("Flow child passed to arrangeOutOfFlow: " + position);
        }
    }

    // ---- rendering and input -------------------------------------------------------

    @Override
    protected void renderChildren(Canvas canvas, Frame frame) {
        boolean clip = style().clip();
        if (clip) {
            canvas.pushClip(contentBounds());
        }
        for (Widget child : children) {
            child.render(canvas, frame);
        }
        if (clip) {
            canvas.popClip();
        }
    }

    @Override
    public @Nullable Widget hitTest(double px, double py) {
        if (!style().visible()) {
            return null;
        }
        boolean inside = bounds().contains(px, py);
        Rect childArea = style().clip() ? contentBounds() : null;
        if (childArea == null || childArea.contains(px, py)) {
            for (int i = children.size() - 1; i >= 0; i--) {
                Widget hit = children.get(i).hitTest(px, py);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return inside ? this : null;
    }
}
