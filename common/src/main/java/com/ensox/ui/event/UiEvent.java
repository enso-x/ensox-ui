package com.ensox.ui.event;

import com.ensox.ui.core.Widget;

/**
 * Base of all input events.
 *
 * <p>Dispatch has two phases. In the <b>intercept</b> phase the event travels
 * from the root down to the target ({@code Widget.intercept}); a container can
 * take the event before its children see it (for example a scroll view that
 * turns a drag into scrolling). In the <b>bubble</b> phase it travels from the
 * target up to the root ({@code Widget.handle} and listeners). Consuming the
 * event stops it in either phase.</p>
 */
public abstract sealed class UiEvent permits PointerEvent, ScrollEvent, KeyEvent, TextEvent {
    private boolean consumed;
    private Widget target;

    public void consume() {
        this.consumed = true;
    }

    public boolean isConsumed() {
        return consumed;
    }

    /** Deepest widget the event was dispatched to. */
    public Widget target() {
        return target;
    }

    public void target(Widget target) {
        this.target = target;
    }
}
