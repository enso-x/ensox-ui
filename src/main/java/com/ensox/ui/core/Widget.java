package com.ensox.ui.core;

import com.ensox.base.Insets;
import com.ensox.ui.event.KeyEvent;
import com.ensox.ui.event.PointerEvent;
import com.ensox.ui.event.ScrollEvent;
import com.ensox.ui.event.TextEvent;
import com.ensox.ui.event.UiEvent;
import com.ensox.ui.geom.Rect;
import com.ensox.ui.paint.Canvas;
import com.ensox.ui.state.Signal;
import com.ensox.ui.state.Subscription;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Base class of every UI element.
 *
 * <h2>Writing a widget</h2>
 * Override only what the widget needs:
 * <ul>
 *     <li>{@link #contentWidth()} / {@link #contentHeight(int)}: intrinsic size
 *     used by {@code Sizing.content()} (padding is added automatically);</li>
 *     <li>{@link #paint(Canvas, Frame)}: drawing between background and foreground;</li>
 *     <li>{@link #onPointer}, {@link #onScroll}, {@link #onKey}, {@link #onText}:
 *     input; consume the event to stop it from bubbling;</li>
 *     <li>{@link #onAttach()} / {@link #onDetach()}: lifecycle.</li>
 * </ul>
 * Users of a widget configure it through {@link #style()}, listeners such as
 * {@link #onClick(Runnable)} and signal bindings ({@link #bind}).
 *
 * <h2>Coordinates</h2>
 * Widgets live in absolute GUI coordinates; {@link #bounds()} is the border box
 * (margin excluded). Layout runs top-down once per frame when something
 * invalidated it: {@link #measure} decides sizes, {@link #arrange} positions.
 */
public abstract class Widget {
    private final Style style = new Style(this);
    private final List<Binding<?>> bindings = new ArrayList<>();
    private final List<Consumer<PointerEvent>> pointerListeners = new ArrayList<>();
    private final List<Consumer<ScrollEvent>> scrollListeners = new ArrayList<>();
    private final List<Consumer<KeyEvent>> keyListeners = new ArrayList<>();
    private final List<Consumer<TextEvent>> textListeners = new ArrayList<>();

    private @Nullable Widget parent;
    private @Nullable UiRoot root;
    private int x;
    private int y;
    private int width;
    private int height;
    private boolean enabled = true;
    private boolean focusable;
    private boolean hovered;
    private boolean focused;

    // ---- configuration ---------------------------------------------------------

    public Style style() {
        return style;
    }

    /**
     * Applies style presets and returns this widget, so construction stays one
     * expression: {@code Label.of(text).style(TITLE, s -> s.margin(...))}.
     */
    @SafeVarargs
    @SuppressWarnings("unchecked")
    public final <W extends Widget> W style(Consumer<Style>... presets) {
        style.apply(presets);
        return (W) this;
    }

    public boolean isEnabled() {
        return enabled && (parent == null || parent.isEnabled());
    }

    /** Disabled widgets (and their descendants) are drawn but receive no input. */
    public Widget enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public boolean isFocusable() {
        return focusable;
    }

    /** Focusable widgets receive key and text input and take part in Tab navigation. */
    public Widget focusable(boolean focusable) {
        this.focusable = focusable;
        return this;
    }

    // ---- tree --------------------------------------------------------------------

    public @Nullable Widget parent() {
        return parent;
    }

    public @Nullable UiRoot root() {
        return root;
    }

    public boolean isAttached() {
        return root != null;
    }

    /** Children for traversal (hit testing, focus order, lifecycle). Leaves have none. */
    public List<Widget> children() {
        return List.of();
    }

    void setParent(@Nullable Widget parent) {
        this.parent = parent;
    }

    void attach(UiRoot root) {
        if (this.root == root) {
            return;
        }
        this.root = root;
        for (Binding<?> binding : bindings) {
            binding.open();
        }
        onAttach();
        for (Widget child : children()) {
            child.attach(root);
        }
    }

    void detach() {
        if (root == null) {
            return;
        }
        for (Widget child : children()) {
            child.detach();
        }
        UiRoot oldRoot = root;
        onDetach();
        for (Binding<?> binding : bindings) {
            binding.close();
        }
        oldRoot.forget(this);
        root = null;
        hovered = false;
        focused = false;
    }

    protected void onAttach() {
    }

    protected void onDetach() {
    }

    /** Requests a new layout pass before the next frame. Cheap; safe to call often. */
    public void invalidateLayout() {
        if (root != null) {
            root.requestLayout();
        }
    }

    // ---- state binding -------------------------------------------------------------

    /**
     * Calls {@code apply} with the signal's current value now and on every
     * change while this widget is attached. The subscription is closed
     * automatically when the widget leaves the tree.
     */
    public <T> Widget bind(Signal<T> signal, Consumer<? super T> apply) {
        Binding<T> binding = new Binding<>(signal, apply);
        bindings.add(binding);
        apply.accept(signal.get());
        if (root != null) {
            binding.open();
        }
        return this;
    }

    private static final class Binding<T> {
        private final Signal<T> signal;
        private final Consumer<? super T> apply;
        private Subscription subscription = Subscription.NONE;

        private Binding(Signal<T> signal, Consumer<? super T> apply) {
            this.signal = signal;
            this.apply = apply;
        }

        void open() {
            subscription.close();
            apply.accept(signal.get());
            subscription = signal.subscribe(apply);
        }

        void close() {
            subscription.close();
            subscription = Subscription.NONE;
        }
    }

    // ---- layout ------------------------------------------------------------------------

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public Rect bounds() {
        return new Rect(x, y, width, height);
    }

    /** Border box shrunk by padding. */
    public Rect contentBounds() {
        return bounds().inset(style.padding());
    }

    /** Width including horizontal margins: the space this widget takes in a flow. */
    public int outerWidth() {
        return width + style.margin().horizontal();
    }

    public int outerHeight() {
        return height + style.margin().vertical();
    }

    /**
     * Decides this widget's size for the space its parent offers (margins
     * included in {@code space}). Called by parents; override
     * {@link #measureSelf} to change the algorithm.
     */
    public final void measure(int spaceWidth, int spaceHeight) {
        Insets margin = style.margin();
        measureSelf(spaceWidth - margin.horizontal(), spaceHeight - margin.vertical(), spaceWidth, spaceHeight);
    }

    /**
     * @param availableWidth  space for this widget without margins
     * @param availableHeight space for this widget without margins
     * @param spaceWidth      space including margins, as offered by the parent
     * @param spaceHeight     space including margins, as offered by the parent
     */
    protected void measureSelf(int availableWidth, int availableHeight, int spaceWidth, int spaceHeight) {
        Insets padding = style.padding();
        int w = style.width().resolve(availableWidth, () -> contentWidth() + padding.horizontal());
        setWidth(w);
        int h = style.height().resolve(availableHeight, () -> contentHeight(this.width - padding.horizontal()) + padding.vertical());
        setHeight(h);
    }

    /** Intrinsic content width, without padding. */
    protected int contentWidth() {
        return 0;
    }

    /** Intrinsic content height for the given content width, without padding. */
    protected int contentHeight(int contentWidth) {
        return 0;
    }

    /** Stores a measured width, clamped to the style limits. */
    protected final void setWidth(int width) {
        this.width = Math.max(0, Math.min(Math.max(width, style.minWidth()), style.maxWidth()));
    }

    protected final void setHeight(int height) {
        this.height = Math.max(0, Math.min(Math.max(height, style.minHeight()), style.maxHeight()));
    }

    /** Positions this widget's border box; containers then position their children. */
    public final void arrange(int x, int y) {
        this.x = x;
        this.y = y;
        arrangeChildren();
    }

    /** Moves this widget and its whole subtree without re-measuring. */
    public void translate(int dx, int dy) {
        if (dx == 0 && dy == 0) {
            return;
        }
        this.x += dx;
        this.y += dy;
        for (Widget child : children()) {
            child.translate(dx, dy);
        }
    }

    protected void arrangeChildren() {
    }

    // ---- rendering ---------------------------------------------------------------------

    /** Per-frame data handed to every widget while rendering. */
    public record Frame(int mouseX, int mouseY, float partialTick, float deltaSeconds) {
    }

    public final void render(Canvas canvas, Frame frame) {
        if (!style.visible()) {
            return;
        }
        Rect bounds = bounds();
        if (!canvas.isVisible(bounds)) {
            return;
        }
        style.background().paint(canvas, bounds);
        paint(canvas, frame);
        renderChildren(canvas, frame);
        style.foreground().paint(canvas, bounds);
    }

    /** Widget-specific drawing between background and children. */
    protected void paint(Canvas canvas, Frame frame) {
    }

    protected void renderChildren(Canvas canvas, Frame frame) {
    }

    // ---- input -----------------------------------------------------------------------------

    /** Deepest visible widget at the point, or {@code null}. */
    public @Nullable Widget hitTest(double px, double py) {
        return style.visible() && bounds().contains(px, py) ? this : null;
    }

    public boolean isHovered() {
        return hovered;
    }

    void setHovered(boolean hovered) {
        if (this.hovered != hovered) {
            this.hovered = hovered;
            onHoverChanged(hovered);
        }
    }

    protected void onHoverChanged(boolean hovered) {
    }

    public boolean isFocused() {
        return focused;
    }

    /** Focused through the keyboard: the state that should be shown as a highlight. */
    public boolean isFocusVisible() {
        return focused && root != null && root.focus().isFocusVisible();
    }

    void setFocused(boolean focused) {
        if (this.focused != focused) {
            this.focused = focused;
            onFocusChanged(focused);
        }
    }

    protected void onFocusChanged(boolean focused) {
    }

    public void requestFocus() {
        if (root != null) {
            root.focus().focus(this);
        }
    }

    /** Intercept phase (root to target): return-free; consume the event to take it. */
    protected void intercept(UiEvent event) {
    }

    final void dispatch(UiEvent event) {
        switch (event) {
            case PointerEvent pointer -> {
                onPointer(pointer);
                notify(pointerListeners, pointer);
            }
            case ScrollEvent scroll -> {
                onScroll(scroll);
                notify(scrollListeners, scroll);
            }
            case KeyEvent key -> {
                onKey(key);
                notify(keyListeners, key);
            }
            case TextEvent text -> {
                onText(text);
                notify(textListeners, text);
            }
        }
    }

    private static <E extends UiEvent> void notify(List<Consumer<E>> listeners, E event) {
        for (int i = 0; i < listeners.size() && !event.isConsumed(); i++) {
            listeners.get(i).accept(event);
        }
    }

    protected void onPointer(PointerEvent event) {
    }

    protected void onScroll(ScrollEvent event) {
    }

    protected void onKey(KeyEvent event) {
    }

    protected void onText(TextEvent event) {
    }

    public Widget onPointerEvent(Consumer<PointerEvent> listener) {
        pointerListeners.add(listener);
        return this;
    }

    public Widget onScrollEvent(Consumer<ScrollEvent> listener) {
        scrollListeners.add(listener);
        return this;
    }

    public Widget onKeyEvent(Consumer<KeyEvent> listener) {
        keyListeners.add(listener);
        return this;
    }

    public Widget onTextEvent(Consumer<TextEvent> listener) {
        textListeners.add(listener);
        return this;
    }

    /**
     * Primary-button click: press and release both inside this widget. The
     * press is consumed, so the widget captures the pointer until release.
     */
    public Widget onClick(Runnable action) {
        return onPointerEvent(event -> {
            if (!event.isPrimary()) {
                return;
            }
            if (event.type() == PointerEvent.Type.PRESS) {
                event.consume();
            } else if (event.type() == PointerEvent.Type.RELEASE && bounds().contains(event.x(), event.y())) {
                event.consume();
                action.run();
            }
        });
    }
}
