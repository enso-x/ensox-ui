package com.ensox.ui.core;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Keyboard focus of one {@link UiRoot}. Key and text events go to the focused
 * widget and bubble up from it. Tab / Shift+Tab move through focusable,
 * enabled, visible widgets in tree order within the active layer.
 */
public final class FocusManager {
    private final UiRoot root;
    private @Nullable Widget focused;
    /** Whether the current focus came from the keyboard; only then is it shown (CSS :focus-visible). */
    private boolean visible;

    FocusManager(UiRoot root) {
        this.root = root;
    }

    public @Nullable Widget focused() {
        return focused;
    }

    public boolean isFocusVisible() {
        return visible && focused != null;
    }

    /** Pointer-driven focus: keyboard input goes to the widget, but no focus highlight is shown. */
    public void focus(@Nullable Widget widget) {
        focus(widget, false);
    }

    public void focus(@Nullable Widget widget, boolean fromKeyboard) {
        this.visible = fromKeyboard;
        if (widget != null && (!widget.isFocusable() || widget.root() != root)) {
            return;
        }
        if (focused == widget) {
            return;
        }
        Widget previous = focused;
        focused = widget;
        if (previous != null) {
            previous.setFocused(false);
        }
        if (widget != null) {
            widget.setFocused(true);
        }
    }

    public void clear() {
        focus(null);
    }

    /** Moves focus forward or backward; wraps around. */
    public void cycle(boolean forward) {
        List<Widget> candidates = new ArrayList<>();
        collect(root.activeLayer(), candidates);
        if (candidates.isEmpty()) {
            return;
        }
        int index = focused == null ? -1 : candidates.indexOf(focused);
        int next = index < 0
            ? (forward ? 0 : candidates.size() - 1)
            : Math.floorMod(index + (forward ? 1 : -1), candidates.size());
        focus(candidates.get(next), true);
    }

    private static void collect(Widget widget, List<Widget> out) {
        if (!widget.style().visible() || !widget.isEnabled()) {
            return;
        }
        if (widget.isFocusable()) {
            out.add(widget);
        }
        for (Widget child : widget.children()) {
            collect(child, out);
        }
    }

    void forget(Widget widget) {
        if (focused == widget) {
            focused = null;
        }
    }
}
