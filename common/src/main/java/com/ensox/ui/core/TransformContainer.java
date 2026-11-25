package com.ensox.ui.core;

import com.ensox.ui.geom.UiTransform;
import com.ensox.ui.paint.Canvas;
import org.jetbrains.annotations.Nullable;

/**
 * A normal widget subtree laid out in logical units and displayed under pan/zoom.
 * Its children retain the root's ordinary focus, capture, bindings and overlays.
 */
public final class TransformContainer extends Container {
    private final Widget content;
    private final int logicalWidth;
    private double offsetX, offsetY;
    private double fractionX, fractionY;
    private float scale = 1;
    private boolean measureDirty = true;
    private boolean arrangeDirty = true;

    public TransformContainer(Widget content, int logicalWidth) {
        if (logicalWidth <= 0) throw new IllegalArgumentException("Logical width must be positive");
        this.content = content;
        this.logicalWidth = logicalWidth;
        add(content);
    }
    public int logicalHeight() { return content.outerHeight(); }
    public int logicalWidth() { return logicalWidth; }
    public void invalidateContent() { measureDirty = true; invalidateLayout(); }
    @Override protected void descendantLayoutInvalidated() { measureDirty = true; }
    /** Layout caches survive viewport movement and off-screen culling. */
    public void measureContent() {
        if (!measureDirty) return;
        content.measure(logicalWidth, 1_000_000);
        measureDirty = false;
        arrangeDirty = true;
    }
    public void place(double x, double y, float scale) {
        new UiTransform(x, y, scale); // Validate before changing the live coordinate space.
        if (!measureDirty && !arrangeDirty && offsetX == x && offsetY == y && this.scale == scale) return;
        this.fractionX = x - Math.floor(x); this.fractionY = y - Math.floor(y); this.scale = scale;
        measureContent();
        updateBounds();
        arrange((int) Math.floor(x), (int) Math.floor(y));
    }
    @Override protected void measureSelf(int width, int height, int spaceWidth, int spaceHeight) {
        measureContent();
        updateBounds();
    }
    private void updateBounds() {
        setWidth((int) Math.ceil(logicalWidth * scale + fractionX));
        setHeight((int) Math.ceil(content.outerHeight() * scale + fractionY));
    }
    @Override protected void arrangeChildren() {
        offsetX = x() + fractionX; offsetY = y() + fractionY;
        if (arrangeDirty) { content.arrange(0, 0); arrangeDirty = false; }
    }
    @Override public void translate(int dx, int dy) { arrange(x() + dx, y() + dy); }
    @Override protected UiTransform childTransform() { return new UiTransform(offsetX, offsetY, scale); }
    @Override protected void renderChildren(Canvas canvas, Frame frame) {
        canvas.pushCoordinateSpace(childTransform());
        try {
            content.render(canvas, new Frame((int) childTransform().unmapX(frame.mouseX()),
                (int) childTransform().unmapY(frame.mouseY()), frame.partialTick(), frame.deltaSeconds()));
        } finally { canvas.popCoordinateSpace(); }
    }
    @Override public @Nullable Widget hitTest(double x, double y) {
        if (!style().visible() || !bounds().contains(x, y)) return null;
        return content.hitTest(childTransform().unmapX(x), childTransform().unmapY(y));
    }
}
