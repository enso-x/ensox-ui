package com.ensox.ui.widget;

import com.ensox.base.ColorX;
import com.ensox.base.Easing;
import com.ensox.base.Insets;
import com.ensox.ui.core.Container;
import com.ensox.ui.core.Widget;
import com.ensox.ui.event.KeyEvent;
import com.ensox.ui.event.PointerEvent;
import com.ensox.ui.event.ScrollEvent;
import com.ensox.ui.event.UiEvent;
import com.ensox.ui.geom.Rect;
import com.ensox.ui.paint.Canvas;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Scrollable viewport around one content widget, on one or both axes.
 *
 * <ul>
 *     <li>The content is measured against the viewport; it overflows (and
 *     becomes scrollable) on scroll axes by being content-sized there.</li>
 *     <li>Wheel, scrollbar drag, arrow keys (when focused) and optional
 *     drag-to-scroll. Drag-to-scroll takes over a gesture only after the
 *     pointer moves 4px, so short clicks still reach the content; the widget
 *     that had the press receives {@code CANCEL}.</li>
 *     <li>Scrolling eases toward the target independent of frame rate, and the
 *     content is drawn with sub-pixel offset for smooth motion.</li>
 * </ul>
 */
public class ScrollView extends Container {
    private static final double DRAG_THRESHOLD_SQUARED = 16;
    private static final double WHEEL_STEP = 15;
    private static final double KEY_STEP = 10;
    /** Exponential approach rate per second; ~10 matches the previous per-tick smoothing at 20 TPS. */
    private static final double SMOOTHING = 10;

    private final Widget content;
    private final boolean horizontal;
    private final boolean vertical;

    private ScrollbarStyle scrollbar = ScrollbarStyle.flat(ColorX.of(0xA0000000));
    private int thickness = 3;
    private int crossOffset;
    private int trackStartOffset;
    private int trackEndOffset;
    private boolean dragToScroll;

    private double targetX;
    private double targetY;
    private double currentX;
    private double currentY;
    private int appliedX;
    private int appliedY;
    private int maxX;
    private int maxY;
    private boolean horizontalActive;
    private boolean verticalActive;
    private long showScrollbarUntil;

    private enum Drag {
        NONE,
        PENDING,
        CONTENT,
        HORIZONTAL_BAR,
        VERTICAL_BAR
    }

    private Drag drag = Drag.NONE;
    private double pressX;
    private double pressY;
    private double lastX;
    private double lastY;

    protected ScrollView(Widget content, boolean horizontal, boolean vertical) {
        this.content = content;
        this.horizontal = horizontal;
        this.vertical = vertical;
        add(content);
        focusable(true);
    }

    public static ScrollView vertical(Widget content) {
        return new ScrollView(content, false, true);
    }

    public static ScrollView horizontal(Widget content) {
        return new ScrollView(content, true, false);
    }

    public static ScrollView both(Widget content) {
        return new ScrollView(content, true, true);
    }

    public Widget content() {
        return content;
    }

    // ---- configuration ---------------------------------------------------------------

    public ScrollView scrollbar(ScrollbarStyle scrollbar) {
        this.scrollbar = scrollbar;
        invalidateLayout();
        return this;
    }

    public ScrollView scrollbarThickness(int thickness) {
        this.thickness = thickness;
        invalidateLayout();
        return this;
    }

    /**
     * @param cross shift across the track (positive = right / down)
     * @param start shift of the track start along the scroll axis
     * @param end   extension of the track end along the scroll axis
     */
    public ScrollView scrollbarOffsets(int cross, int start, int end) {
        this.crossOffset = cross;
        this.trackStartOffset = start;
        this.trackEndOffset = end;
        return this;
    }

    /** Lets the user drag the content itself; keep off when children own drag gestures. */
    public ScrollView dragToScroll(boolean dragToScroll) {
        this.dragToScroll = dragToScroll;
        return this;
    }

    public double scrollX() {
        return targetX;
    }

    public double scrollY() {
        return targetY;
    }

    public int maxScrollX() {
        return maxX;
    }

    public int maxScrollY() {
        return maxY;
    }

    public boolean isScrollActive() {
        return horizontalActive || verticalActive;
    }

    public void scrollTo(double x, double y, boolean animate) {
        targetX = Mth.clamp(x, 0, maxX);
        targetY = Mth.clamp(y, 0, maxY);
        if (!animate) {
            currentX = targetX;
            currentY = targetY;
        }
    }

    public void scrollBy(double dx, double dy, boolean animate) {
        scrollTo(targetX + dx, targetY + dy, animate);
        showScrollbarUntil = System.currentTimeMillis() + 1250;
    }

    // ---- layout ------------------------------------------------------------------------------

    private int reserved() {
        return scrollbar.reservedSpace(thickness);
    }

    private int viewportWidth() {
        return Math.max(0, width() - style().padding().horizontal() - (verticalActive ? reserved() : 0));
    }

    private int viewportHeight() {
        return Math.max(0, height() - style().padding().vertical() - (horizontalActive ? reserved() : 0));
    }

    private Rect viewport() {
        Insets padding = style().padding();
        return Rect.of(x() + padding.left(), y() + padding.top(), viewportWidth(), viewportHeight());
    }

    @Override
    protected void measureSelf(int availableWidth, int availableHeight, int spaceWidth, int spaceHeight) {
        Insets padding = style().padding();
        boolean contentWidth = style().width().isContent() && !horizontal;
        boolean contentHeight = style().height().isContent() && !vertical;
        if (!contentWidth) {
            setWidth(style().width().resolve(availableWidth, () -> 0));
        }
        if (!contentHeight) {
            setHeight(style().height().resolve(availableHeight, () -> 0));
        }

        boolean h = false;
        boolean v = false;
        for (int pass = 0; pass < 4; pass++) {
            int viewportW = (contentWidth ? spaceWidth : width()) - padding.horizontal() - (v ? reserved() : 0);
            int viewportH = (contentHeight ? spaceHeight : height()) - padding.vertical() - (h ? reserved() : 0);
            content.measure(Math.max(0, viewportW), Math.max(0, viewportH));
            boolean nextH = horizontal && content.outerWidth() > viewportW;
            boolean nextV = vertical && content.outerHeight() > viewportH;
            if (nextH == h && nextV == v) {
                break;
            }
            h = nextH;
            v = nextV;
        }
        horizontalActive = h;
        verticalActive = v;

        if (contentWidth) {
            setWidth(style().width().resolve(availableWidth,
                () -> content.outerWidth() + padding.horizontal() + (verticalActive ? reserved() : 0)));
        }
        if (contentHeight) {
            setHeight(style().height().resolve(availableHeight,
                () -> content.outerHeight() + padding.vertical() + (horizontalActive ? reserved() : 0)));
        }

        maxX = horizontalActive ? Math.max(0, content.outerWidth() - viewportWidth()) : 0;
        maxY = verticalActive ? Math.max(0, content.outerHeight() - viewportHeight()) : 0;
        targetX = Mth.clamp(targetX, 0, maxX);
        targetY = Mth.clamp(targetY, 0, maxY);
        currentX = Mth.clamp(currentX, 0, maxX);
        currentY = Mth.clamp(currentY, 0, maxY);
    }

    @Override
    protected void arrangeChildren() {
        Insets padding = style().padding();
        Insets margin = content.style().margin();
        appliedX = (int) Math.floor(currentX);
        appliedY = (int) Math.floor(currentY);
        content.arrange(x() + padding.left() + margin.left() - appliedX, y() + padding.top() + margin.top() - appliedY);
    }

    // ---- rendering -------------------------------------------------------------------------

    private void advanceScroll(float deltaSeconds) {
        double factor = 1 - Math.exp(-SMOOTHING * deltaSeconds);
        currentX = approach(currentX, targetX, factor);
        currentY = approach(currentY, targetY, factor);
        int nextX = (int) Math.floor(currentX);
        int nextY = (int) Math.floor(currentY);
        if (nextX != appliedX || nextY != appliedY) {
            content.translate(appliedX - nextX, appliedY - nextY);
            appliedX = nextX;
            appliedY = nextY;
        }
    }

    private static double approach(double current, double target, double factor) {
        double next = current + (target - current) * factor;
        return Math.abs(target - next) < 0.01 ? target : next;
    }

    @Override
    protected void renderChildren(Canvas canvas, Frame frame) {
        advanceScroll(frame.deltaSeconds());
        Rect viewport = viewport();
        if (style().clip()) {
            canvas.pushClip(viewport);
        }
        float fracX = (float) -(currentX - Math.floor(currentX));
        float fracY = (float) -(currentY - Math.floor(currentY));
        canvas.pushTranslation(fracX, fracY);
        content.render(canvas, frame);
        canvas.popTransform();
        if (style().clip()) {
            canvas.popClip();
        }

        if (isInScrollbar(frame.mouseX(), frame.mouseY()) != null || drag == Drag.HORIZONTAL_BAR || drag == Drag.VERTICAL_BAR) {
            showScrollbarUntil = System.currentTimeMillis() + 1500;
        }
        float visibility = Easing.SINE.apply(Mth.clamp(showScrollbarUntil - System.currentTimeMillis(), 0, 750) / 750f);
        if (verticalActive) {
            Rect track = verticalTrack();
            int handle = handleLength(viewportHeight(), content.outerHeight(), track.height());
            int offset = handleOffset(currentY, maxY, track.height(), handle);
            scrollbar.paint(canvas, Rect.of(track.x(), track.y() + offset, thickness, handle), track, true, visibility);
        }
        if (horizontalActive) {
            Rect track = horizontalTrack();
            int handle = handleLength(viewportWidth(), content.outerWidth(), track.width());
            int offset = handleOffset(currentX, maxX, track.width(), handle);
            scrollbar.paint(canvas, Rect.of(track.x() + offset, track.y(), handle, thickness), track, false, visibility);
        }
    }

    private Rect verticalTrack() {
        Insets padding = style().padding();
        int length = Math.max(0, viewportHeight() + trackEndOffset - trackStartOffset);
        return Rect.of(x() + width() - padding.right() - thickness + crossOffset, y() + padding.top() + trackStartOffset, thickness, length);
    }

    private Rect horizontalTrack() {
        Insets padding = style().padding();
        int length = Math.max(0, viewportWidth() + trackEndOffset - trackStartOffset);
        return Rect.of(x() + padding.left() + trackStartOffset, y() + height() - padding.bottom() - thickness + crossOffset, length, thickness);
    }

    private int handleLength(int viewport, int contentSize, int track) {
        if (contentSize <= 0) {
            return track;
        }
        return (int) Math.max(thickness, Math.min(Math.floor(((float) viewport / contentSize) * track), track));
    }

    private static int handleOffset(double scroll, int max, int track, int handle) {
        if (max <= 0 || track <= handle) {
            return 0;
        }
        return (int) ((scroll / max) * (track - handle));
    }

    private double barScalar(boolean verticalBar) {
        Rect track = verticalBar ? verticalTrack() : horizontalTrack();
        int length = verticalBar ? track.height() : track.width();
        int handle = verticalBar
            ? handleLength(viewportHeight(), content.outerHeight(), length)
            : handleLength(viewportWidth(), content.outerWidth(), length);
        double scalar = (verticalBar ? maxY : maxX) / (double) Math.max(1, length - handle);
        return Double.isFinite(scalar) ? scalar : 0;
    }

    private @Nullable Drag isInScrollbar(double px, double py) {
        if (verticalActive && verticalTrack().contains(px, py)) {
            return Drag.VERTICAL_BAR;
        }
        if (horizontalActive && horizontalTrack().contains(px, py)) {
            return Drag.HORIZONTAL_BAR;
        }
        return null;
    }

    // ---- input -----------------------------------------------------------------------------------

    @Override
    public @Nullable Widget hitTest(double px, double py) {
        if (!style().visible() || !bounds().contains(px, py)) {
            return null;
        }
        if (isInScrollbar(px, py) != null) {
            return this;
        }
        if (viewport().contains(px, py)) {
            Widget hit = content.hitTest(px, py);
            if (hit != null) {
                return hit;
            }
        }
        return this;
    }

    @Override
    protected void intercept(UiEvent event) {
        if (!(event instanceof PointerEvent pointer) || !dragToScroll || !isScrollActive()) {
            return;
        }
        switch (pointer.type()) {
            case PRESS -> {
                if (pointer.isPrimary() && viewport().contains(pointer.x(), pointer.y())) {
                    drag = Drag.PENDING;
                    pressX = lastX = pointer.x();
                    pressY = lastY = pointer.y();
                }
            }
            case DRAG -> {
                if (drag == Drag.PENDING) {
                    double dx = pointer.x() - pressX;
                    double dy = pointer.y() - pressY;
                    if (dx * dx + dy * dy < DRAG_THRESHOLD_SQUARED) {
                        return;
                    }
                    drag = Drag.CONTENT;
                }
                if (drag == Drag.CONTENT) {
                    scrollBy(horizontalActive ? lastX - pointer.x() : 0, verticalActive ? lastY - pointer.y() : 0, false);
                    lastX = pointer.x();
                    lastY = pointer.y();
                    pointer.consume();
                }
            }
            default -> {
            }
        }
    }

    @Override
    protected void onPointer(PointerEvent event) {
        switch (event.type()) {
            case PRESS -> {
                if (!event.isPrimary()) {
                    return;
                }
                Drag bar = isInScrollbar(event.x(), event.y());
                if (bar != null) {
                    drag = bar;
                    event.consume();
                } else if (drag == Drag.PENDING) {
                    // Nothing below took the press: capture it so drag-to-scroll receives the drag.
                    event.consume();
                }
            }
            case DRAG -> {
                if (drag == Drag.VERTICAL_BAR) {
                    scrollBy(0, event.deltaY() * barScalar(true), false);
                    event.consume();
                } else if (drag == Drag.HORIZONTAL_BAR) {
                    scrollBy(event.deltaX() * barScalar(false), 0, false);
                    event.consume();
                }
            }
            case RELEASE, CANCEL -> {
                if (drag != Drag.NONE && drag != Drag.PENDING) {
                    event.consume();
                }
                drag = Drag.NONE;
            }
            default -> {
            }
        }
    }

    @Override
    protected void onScroll(ScrollEvent event) {
        double dx = -event.amountX() * WHEEL_STEP;
        double dy = -event.amountY() * WHEEL_STEP;
        if (!verticalActive && horizontalActive && dx == 0) {
            dx = dy;
            dy = 0;
        }
        boolean canMoveX = horizontalActive && dx != 0 && (dx < 0 ? targetX > 0 : targetX < maxX);
        boolean canMoveY = verticalActive && dy != 0 && (dy < 0 ? targetY > 0 : targetY < maxY);
        if (canMoveX || canMoveY) {
            scrollBy(canMoveX ? dx : 0, canMoveY ? dy : 0, true);
            event.consume();
        }
    }

    @Override
    protected void onKey(KeyEvent event) {
        if (event.type() != KeyEvent.Type.PRESS || !isFocused()) {
            return;
        }
        switch (event.key()) {
            case GLFW.GLFW_KEY_UP -> scrollBy(0, -KEY_STEP, true);
            case GLFW.GLFW_KEY_DOWN -> scrollBy(0, KEY_STEP, true);
            case GLFW.GLFW_KEY_LEFT -> scrollBy(-KEY_STEP, 0, true);
            case GLFW.GLFW_KEY_RIGHT -> scrollBy(KEY_STEP, 0, true);
            default -> {
                return;
            }
        }
        event.consume();
    }
}
