package com.ensox.base;

import net.minecraft.util.Mth;

public record Point(int x, int y) implements Animatable<Point> {
    @Override
    public Point interpolate(Point next, float delta) {
        return Point.of(
            Mth.lerpInt(delta, this.x(), next.x()),
            Mth.lerpInt(delta, this.y(), next.y())
        );
    }

    @Override
    public String toString() {
        return "Point{ x: " + x + ", y: " + y + " }";
    }

    public static Point of(int x, int y) {
        return new Point(x, y);
    }
}
