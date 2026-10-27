package com.ensox.ui.widget;

import com.ensox.ui.core.Container;
import com.ensox.ui.core.UiRoot;
import com.ensox.ui.core.Widget;
import com.ensox.ui.geom.Rect;
import com.ensox.ui.paint.Canvas;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Overlay content attached to an anchor widget (dropdowns, pickers, editors).
 *
 * <p>Placement: left-aligned with the anchor, below it if the content fits,
 * otherwise above, otherwise on the side with more room; always clamped into
 * the visible area. The position is re-evaluated every frame, so the popup
 * follows its anchor while a scroll view moves it.</p>
 *
 * <pre>
 * Popup popup = Popup.open(selectField, listWidget, () -&gt; screenArea, this::onClosed);
 * popup.close();
 * </pre>
 */
public final class Popup extends Container {
    private final Widget anchor;
    private final Widget content;
    private final Supplier<Rect> visibleArea;
    private final boolean alignRight;
    private @Nullable UiRoot.Overlay overlay;

    private Popup(Widget anchor, Widget content, Supplier<Rect> visibleArea, boolean alignRight) {
        this.anchor = anchor;
        this.content = content;
        this.visibleArea = visibleArea;
        this.alignRight = alignRight;
        add(content);
        style().overflow();
    }

    /**
     * @param visibleArea area the popup must stay inside, or {@code null} for the whole screen
     * @param onClose     runs once when the popup closes for any reason
     */
    public static Popup open(Widget anchor, Widget content, @Nullable Supplier<Rect> visibleArea, @Nullable Runnable onClose) {
        return open(anchor, content, visibleArea, onClose, false);
    }

    /** @param alignRight align the popup's right edge with the anchor's right edge instead of the left edges */
    public static Popup open(Widget anchor, Widget content, @Nullable Supplier<Rect> visibleArea,
                             @Nullable Runnable onClose, boolean alignRight) {
        UiRoot root = anchor.root();
        if (root == null) {
            throw new IllegalStateException("Popup anchor is not attached");
        }
        Supplier<Rect> area = visibleArea != null ? visibleArea : () -> Rect.of(0, 0, root.width(), root.height());
        Popup popup = new Popup(anchor, content, area, alignRight);
        popup.overlay = root.openOverlay(popup, true, onClose);
        return popup;
    }

    public boolean isOpen() {
        return overlay != null && overlay.isOpen();
    }

    public void close() {
        if (overlay != null) {
            overlay.close();
        }
    }

    @Override
    protected void measureSelf(int availableWidth, int availableHeight, int spaceWidth, int spaceHeight) {
        content.measure(spaceWidth, spaceHeight);
        setWidth(spaceWidth);
        setHeight(spaceHeight);
    }

    @Override
    protected void arrangeChildren() {
        Rect target = placement();
        content.arrange(target.x(), target.y());
    }

    private Rect placement() {
        Rect area = visibleArea.get();
        Rect a = anchor.bounds();
        int w = content.outerWidth();
        int h = content.outerHeight();
        int preferredX = alignRight ? a.right() - w : a.x();
        int x = Math.max(area.x(), Math.min(preferredX, area.right() - w));
        int below = a.bottom();
        int above = a.y() - h;
        int roomDown = area.bottom() - below;
        int roomUp = a.y() - area.y();
        int y = h <= roomDown ? below : h <= roomUp ? above : (roomDown >= roomUp ? below : above);
        y = Math.max(area.y(), Math.min(y, area.bottom() - h));
        return Rect.of(x + content.style().margin().left(), y + content.style().margin().top(), w, h);
    }

    @Override
    protected void renderChildren(Canvas canvas, Frame frame) {
        // Follow the anchor when it moved without a relayout (scrolling).
        Rect target = placement();
        content.translate(target.x() - content.x(), target.y() - content.y());
        content.render(canvas, frame);
    }

    @Override
    public @Nullable Widget hitTest(double px, double py) {
        // The popup layer itself is transparent; only its content takes input.
        return content.hitTest(px, py);
    }
}
