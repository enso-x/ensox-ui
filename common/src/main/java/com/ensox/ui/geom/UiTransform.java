package com.ensox.ui.geom;

/** Uniform scale and translation between a widget coordinate space and its parent. */
public record UiTransform(double x, double y, double scale) {
    public static final UiTransform IDENTITY = new UiTransform(0, 0, 1);
    public UiTransform {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(scale) || scale <= 0)
            throw new IllegalArgumentException("UI transform must be finite with positive scale");
    }
    public double mapX(double value) { return x + value * scale; }
    public double mapY(double value) { return y + value * scale; }
    public double unmapX(double value) { return (value - x) / scale; }
    public double unmapY(double value) { return (value - y) / scale; }
    public UiTransform then(UiTransform child) {
        return new UiTransform(mapX(child.x), mapY(child.y), scale * child.scale);
    }
    /** Round outward so fractional zoom never clips a painted edge or caret. */
    public Rect map(Rect bounds) {
        int left = (int) Math.floor(mapX(bounds.x())), top = (int) Math.floor(mapY(bounds.y()));
        return Rect.of(left, top, (int) Math.ceil(mapX(bounds.right())) - left,
            (int) Math.ceil(mapY(bounds.bottom())) - top);
    }
}
