package com.ensox.ui.core;

import com.ensox.ui.anim.FrameTask;
import com.ensox.ui.event.KeyEvent;
import com.ensox.ui.event.Modifiers;
import com.ensox.ui.event.PointerEvent;
import com.ensox.ui.event.ScrollEvent;
import com.ensox.ui.event.TextEvent;
import com.ensox.ui.event.UiEvent;
import com.ensox.ui.layout.Position;
import com.ensox.ui.geom.UiTransform;
import com.ensox.ui.paint.Canvas;
import com.mojang.blaze3d.platform.cursor.CursorType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Owner of one widget tree: layout, layers, input routing, focus, hover,
 * cursor, tooltips and animations. Hosts (usually {@link UiScreen}) forward
 * raw input and the render call; everything else happens here.
 *
 * <h2>Layers</h2>
 * The content tree is the bottom layer. {@link #openOverlay} pushes widgets
 * (dropdowns, context menus, dialogs) above it; they are drawn in a later GUI
 * stratum, receive input first, and can close on outside clicks or Escape.
 */
public final class UiRoot {
    /** Widest widget tooltip line before it wraps. */
    private static final int TOOLTIP_WIDTH = 200;
    /** Sprite style of widget tooltips (vanilla's {@code <ns>:tooltip/<path>_background} and {@code _frame}). */
    private static @Nullable Identifier tooltipStyle;

    private final Widget content;
    private final List<Overlay> overlays = new ArrayList<>();
    private final FocusManager focus = new FocusManager(this);
    private final List<FrameTask> animations = new ArrayList<>();
    private final Set<Widget> hoveredPath = new HashSet<>();

    private int width;
    private int height;
    private boolean layoutDirty = true;
    private long lastFrameNanos;
    private double mouseX = -1;
    private double mouseY = -1;
    private @Nullable Widget hovered;
    private @Nullable Widget captured;
    private int capturedButton = -1;

    public UiRoot(Widget content) {
        this.content = content;
        content.attach(this);
    }

    public Widget content() {
        return content;
    }

    public FocusManager focus() {
        return focus;
    }

    /** Frame tasks ticked before layout every frame (see {@link com.ensox.ui.anim.Animated}). */
    public List<FrameTask> animations() {
        return animations;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public void resize(int width, int height) {
        if (this.width != width || this.height != height) {
            this.width = width;
            this.height = height;
            layoutDirty = true;
        }
    }

    public void requestLayout() {
        layoutDirty = true;
    }

    /** Re-attaches the tree after {@link #detach()}, restoring bindings. */
    public void attach() {
        content.attach(this);
        for (Overlay overlay : overlays) {
            overlay.widget.attach(this);
        }
        layoutDirty = true;
    }

    /** Detaches every widget: bindings close and nothing references the host anymore. */
    public void detach() {
        for (Overlay overlay : List.copyOf(overlays)) {
            overlay.widget.detach();
        }
        content.detach();
        hovered = null;
        captured = null;
        hoveredPath.clear();
    }

    void forget(Widget widget) {
        focus.forget(widget);
        hoveredPath.remove(widget);
        if (hovered == widget) {
            hovered = null;
        }
        if (captured == widget) {
            captured = null;
        }
    }

    // ---- layers ---------------------------------------------------------------------

    public final class Overlay {
        private final Widget widget;
        private final boolean dismissOnOutsideClick;
        private final @Nullable Runnable onClose;
        private final List<Widget> alsoInside = new ArrayList<>();

        private Overlay(Widget widget, boolean dismissOnOutsideClick, @Nullable Runnable onClose) {
            this.widget = widget;
            this.dismissOnOutsideClick = dismissOnOutsideClick;
            this.onClose = onClose;
        }

        public Widget widget() {
            return widget;
        }

        public boolean isOpen() {
            return overlays.contains(this);
        }

        /** Clicks on {@code outside} and its descendants do not count as outside clicks. */
        public void keepOpenOn(Widget outside) {
            alsoInside.add(outside);
        }

        private boolean contains(@Nullable Widget target) {
            if (isDescendant(target, widget)) {
                return true;
            }
            for (Widget extra : alsoInside) {
                if (isDescendant(target, extra)) {
                    return true;
                }
            }
            return false;
        }

        public void close() {
            if (overlays.remove(this)) {
                widget.detach();
                if (onClose != null) {
                    onClose.run();
                }
                layoutDirty = true;
            }
        }
    }

    /**
     * Shows {@code widget} above everything else. Place it with
     * {@code style().position(Position.absolute(x, y))} in screen coordinates.
     */
    public Overlay openOverlay(Widget widget, boolean dismissOnOutsideClick, @Nullable Runnable onClose) {
        Overlay overlay = new Overlay(widget, dismissOnOutsideClick, onClose);
        overlays.add(overlay);
        widget.attach(this);
        layoutDirty = true;
        return overlay;
    }

    public List<Overlay> overlays() {
        return Collections.unmodifiableList(overlays);
    }

    /** Topmost layer: the last overlay, or the content. */
    Widget activeLayer() {
        return overlays.isEmpty() ? content : overlays.getLast().widget;
    }

    /**
     * Styles every widget tooltip with the theme's sprites ({@code null}: vanilla). Item tooltips drawn by
     * the game keep their own style.
     */
    public static void tooltipStyle(@Nullable Identifier style) {
        tooltipStyle = style;
    }

    // ---- frame -------------------------------------------------------------------------

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float deltaSeconds = lastFrameNanos == 0 ? 0f : Math.min(0.25f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;

        tickAnimations(deltaSeconds);
        layoutIfNeeded();
        updateHover(mouseX, mouseY);

        Canvas canvas = new Canvas(graphics);
        Widget.Frame frame = new Widget.Frame(mouseX, mouseY, partialTick, deltaSeconds);
        content.render(canvas, frame);
        for (Overlay overlay : overlays) {
            graphics.nextStratum();
            overlay.widget.render(canvas, frame);
        }

        CursorType cursor = inherited(hovered, w -> w.style().cursor());
        if (cursor != null) {
            graphics.requestCursor(cursor);
        }
        Supplier<List<Component>> tooltip = inherited(hovered, w -> w.style().tooltip());
        if (tooltip != null && captured == null) {
            List<Component> lines = tooltip.get();
            if (lines != null && !lines.isEmpty()) {
                Font font = Minecraft.getInstance().font;
                List<FormattedCharSequence> wrapped = new ArrayList<>();
                for (Component line : lines) {
                    wrapped.addAll(line.getString().isEmpty() ? List.of(FormattedCharSequence.EMPTY) : font.split(line, TOOLTIP_WIDTH));
                }
                graphics.setTooltipForNextFrame(font, wrapped, mouseX, mouseY, tooltipStyle);
            }
        }
    }

    private void tickAnimations(float deltaSeconds) {
        if (animations.isEmpty()) {
            return;
        }
        for (FrameTask task : List.copyOf(animations)) {
            if (!task.tick(deltaSeconds)) {
                animations.remove(task);
            }
        }
    }

    public void layoutIfNeeded() {
        if (!layoutDirty) {
            return;
        }
        layoutDirty = false;
        layoutLayer(content);
        for (Overlay overlay : overlays) {
            layoutLayer(overlay.widget);
        }
    }

    private void layoutLayer(Widget widget) {
        widget.measure(width, height);
        Position position = widget.style().position();
        int x = widget.style().margin().left();
        int y = widget.style().margin().top();
        if (position.kind() == Position.Kind.ABSOLUTE) {
            x += position.x();
            y += position.y();
        }
        widget.arrange(x, y);
    }

    private static <T> @Nullable T inherited(@Nullable Widget start, java.util.function.Function<Widget, T> getter) {
        for (Widget w = start; w != null; w = w.parent()) {
            T value = getter.apply(w);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    // ---- input ---------------------------------------------------------------------------

    private @Nullable Widget hitTest(double x, double y) {
        for (int i = overlays.size() - 1; i >= 0; i--) {
            Widget hit = overlays.get(i).widget.hitTest(x, y);
            if (hit != null) {
                return hit;
            }
        }
        return content.hitTest(x, y);
    }

    private void updateHover(double x, double y) {
        this.mouseX = x;
        this.mouseY = y;
        Widget target = captured != null ? captured : hitTest(x, y);
        hovered = target;
        Set<Widget> path = new HashSet<>();
        for (Widget w = target; w != null; w = w.parent()) {
            path.add(w);
        }
        for (Widget old : List.copyOf(hoveredPath)) {
            if (!path.contains(old)) {
                old.setHovered(false);
            }
        }
        for (Widget now : path) {
            now.setHovered(true);
        }
        hoveredPath.clear();
        hoveredPath.addAll(path);
    }

    public boolean pointerPressed(double x, double y, int button, int modifiers, boolean doubleClick) {
        layoutIfNeeded();
        Widget target = hitTest(x, y);

        if (!overlays.isEmpty()) {
            Overlay top = overlays.getLast();
            if (top.dismissOnOutsideClick && !top.contains(target)) {
                top.close();
                return true;
            }
        }

        Widget focusTarget = target;
        while (focusTarget != null && !focusTarget.isFocusable()) {
            focusTarget = focusTarget.parent();
        }
        if (focusTarget != null && focusTarget.isEnabled()) {
            focus.focus(focusTarget);
        } else {
            focus.clear();
        }

        PointerEvent event = new PointerEvent(PointerEvent.Type.PRESS, x, y, button, new Modifiers(modifiers), doubleClick, 0, 0);
        Widget handler = dispatch(target, event);
        if (handler != null) {
            captured = handler;
            capturedButton = button;
        }
        return event.isConsumed();
    }

    public boolean pointerReleased(double x, double y, int button, int modifiers) {
        Widget target = captured != null && capturedButton == button ? captured : hitTest(x, y);
        if (capturedButton == button) {
            captured = null;
            capturedButton = -1;
        }
        PointerEvent event = new PointerEvent(PointerEvent.Type.RELEASE, x, y, button, new Modifiers(modifiers), false, 0, 0);
        dispatch(target, event);
        return event.isConsumed();
    }

    public boolean pointerDragged(double x, double y, int button, int modifiers, double dx, double dy) {
        if (captured == null) {
            return false;
        }
        PointerEvent event = new PointerEvent(PointerEvent.Type.DRAG, x, y, button, new Modifiers(modifiers), false, dx, dy);
        Widget handler = dispatch(captured, event);
        if (handler != null && handler != captured) {
            // An ancestor intercepted the drag: it now owns the gesture.
            Widget previous = captured;
            captured = handler;
            deliver(previous, new PointerEvent(PointerEvent.Type.CANCEL, x, y, button, new Modifiers(modifiers), false, 0, 0), false);
        }
        return event.isConsumed();
    }

    public void pointerMoved(double x, double y) {
        if (captured != null) {
            return;
        }
        updateHover(x, y);
        PointerEvent event = new PointerEvent(PointerEvent.Type.MOVE, x, y, -1, Modifiers.NONE, false, 0, 0);
        dispatch(hovered, event);
    }

    public boolean scrolled(double x, double y, double amountX, double amountY) {
        layoutIfNeeded();
        ScrollEvent event = new ScrollEvent(x, y, amountX, amountY, Modifiers.NONE);
        dispatch(hitTest(x, y), event);
        return event.isConsumed();
    }

    public boolean keyPressed(net.minecraft.client.input.KeyEvent vanilla) {
        KeyEvent event = new KeyEvent(KeyEvent.Type.PRESS, vanilla);
        dispatch(focus.focused(), event);
        if (event.isConsumed()) {
            return true;
        }
        if (vanilla.key() == InputConstants.KEY_TAB) {
            focus.cycle(!vanilla.hasShiftDown());
            return true;
        }
        if (vanilla.key() == InputConstants.KEY_ESCAPE && !overlays.isEmpty()) {
            overlays.getLast().close();
            return true;
        }
        return false;
    }

    public boolean keyReleased(net.minecraft.client.input.KeyEvent vanilla) {
        KeyEvent event = new KeyEvent(KeyEvent.Type.RELEASE, vanilla);
        dispatch(focus.focused(), event);
        return event.isConsumed();
    }

    public boolean textTyped(int codepoint, int modifiers) {
        TextEvent event = new TextEvent(codepoint, new Modifiers(modifiers));
        dispatch(focus.focused(), event);
        return event.isConsumed();
    }

    public double mouseX() {
        return mouseX;
    }

    /** Deepest widget under the pointer (or the capturing widget while dragging). */
    public @Nullable Widget hovered() {
        return hovered;
    }

    /** Whether a widget currently owns the pointer (a drag is in progress). */
    public boolean isPointerCaptured() {
        return captured != null;
    }

    public double mouseY() {
        return mouseY;
    }

    /**
     * Intercept phase from the layer root down to the target, then bubble
     * from the target up. Disabled widgets are skipped.
     *
     * @return the widget that consumed the event, or {@code null}
     */
    private @Nullable Widget dispatch(@Nullable Widget target, UiEvent event) {
        if (target == null) {
            return null;
        }
        event.target(target);
        List<Widget> path = new ArrayList<>();
        for (Widget w = target; w != null; w = w.parent()) {
            path.add(w);
        }
        for (int i = path.size() - 1; i >= 0; i--) {
            Widget w = path.get(i);
            if (w.isEnabled()) {
                deliver(w, event, true);
                if (event.isConsumed()) {
                    return w;
                }
            }
        }
        for (Widget w : path) {
            if (w.isEnabled()) {
                deliver(w, event, false);
                if (event.isConsumed()) {
                    return w;
                }
            }
        }
        return null;
    }

    /** Events are screen-based at the root and local to each receiving subtree. */
    private static void deliver(Widget receiver, UiEvent original, boolean intercept) {
        UiTransform transform = receiver.screenTransform();
        UiEvent local = original;
        if (!transform.equals(UiTransform.IDENTITY)) {
            if (original instanceof PointerEvent event) {
                local = new PointerEvent(event.type(), transform.unmapX(event.x()), transform.unmapY(event.y()),
                    event.button(), event.modifiers(), event.isDoubleClick(),
                    event.deltaX() / transform.scale(), event.deltaY() / transform.scale());
            } else if (original instanceof ScrollEvent event) {
                local = new ScrollEvent(transform.unmapX(event.x()), transform.unmapY(event.y()),
                    event.amountX(), event.amountY(), event.modifiers());
            }
            local.target(original.target());
        }
        if (intercept) receiver.intercept(local); else receiver.dispatch(local);
        if (local.isConsumed()) original.consume();
    }

    private static boolean isDescendant(@Nullable Widget widget, Widget ancestor) {
        for (Widget w = widget; w != null; w = w.parent()) {
            if (w == ancestor) {
                return true;
            }
        }
        return false;
    }
}
