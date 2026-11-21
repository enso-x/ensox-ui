package com.ensox.ui.layout;

import com.ensox.ui.geom.Rect;

import java.util.ArrayList;
import java.util.List;

/**
 * Places round buttons on an arc around a point (a radial context menu).
 *
 * <p>The spacing comes from the button size alone: neighbouring centres are
 * {@code 2 * radius + gap} apart, the arc starts at {@code radius + clearance} from the point (a ring never
 * covers it), and grows only when the buttons no longer fit into {@code maxSpanDegrees}:</p>
 * <pre>
 * chord = 2 * radius + gap
 * step  = 2 * asin(chord / (2 * R))
 * R     = max(radius + clearance, chord / (2 * sin(span / (2 * (n - 1)))))
 * </pre>
 * <p>The arc is centred straight up. When it would leave the screen it turns (inwards at the sides, down at
 * the top) instead of squeezing the spacing. Where no direction fits (a click right at a corner), the
 * direction that leaves the screen least is shifted inside as a whole.</p>
 */
public final class RadialLayout {
    /** Button radius, gap between neighbouring buttons, clearance from the point, widest arc. */
    public record Spec(int radius, int gap, int clearance, double maxSpanDegrees) {
        public Spec {
            if (radius < 1 || gap < 0 || clearance < 0 || maxSpanDegrees <= 0 || maxSpanDegrees > 360) {
                throw new IllegalArgumentException("Invalid radial spec");
            }
        }
    }

    public record Centre(int x, int y) { }

    /** @param arcRadius distance from the point to every centre */
    public record Placement(double arcRadius, List<Centre> centres) { }

    private static final double UP = -Math.PI / 2;
    private static final double LEAN = Math.toRadians(45);

    private RadialLayout() {
    }

    /** Radius of the arc that fits {@code count} buttons of {@code spec}. */
    public static double arcRadius(int count, Spec spec) {
        double radius = spec.radius() + spec.clearance();
        if (count < 2) {
            return radius;
        }
        double chord = 2.0 * spec.radius() + spec.gap();
        double span = Math.toRadians(spec.maxSpanDegrees());
        double needed = chord / (2 * Math.sin(span / (2 * (count - 1))));
        return chord / (2 * radius) >= 1 || (count - 1) * step(chord, radius) > span ? Math.max(radius, needed) : radius;
    }

    public static Placement place(int count, int x, int y, Rect screen, Spec spec) {
        if (count < 1) {
            throw new IllegalArgumentException("A radial menu needs at least one button");
        }
        double radius = arcRadius(count, spec);
        double step = count < 2 ? 0 : step(2.0 * spec.radius() + spec.gap(), radius);
        double reach = radius + spec.radius() + 1;
        List<Double> directions = new ArrayList<>();
        if (x - reach < screen.x()) directions.add(UP + LEAN);
        if (x + reach > screen.right()) directions.add(UP - LEAN);
        directions.addAll(List.of(UP, UP + LEAN, UP - LEAN, Math.PI / 2, 0.0, Math.PI));
        List<Centre> best = null;
        int bestOverflow = Integer.MAX_VALUE;
        for (double direction : directions) {
            List<Centre> centres = centres(count, x, y, radius, step, direction);
            int overflow = overflow(centres, screen, spec.radius());
            if (overflow == 0) {
                return new Placement(radius, centres);
            }
            if (overflow < bestOverflow) {
                best = centres;
                bestOverflow = overflow;
            }
        }
        return new Placement(radius, shiftInside(best, screen, spec.radius()));
    }

    private static double step(double chord, double radius) {
        return 2 * Math.asin(Math.min(1, chord / (2 * radius)));
    }

    private static List<Centre> centres(int count, int x, int y, double radius, double step, double direction) {
        double first = direction - (count - 1) * step / 2;
        List<Centre> centres = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = first + i * step;
            centres.add(new Centre((int) Math.round(x + Math.cos(angle) * radius), (int) Math.round(y + Math.sin(angle) * radius)));
        }
        return centres;
    }

    /** Pixels the buttons stick out of the screen, summed over all buttons and sides. */
    private static int overflow(List<Centre> centres, Rect screen, int radius) {
        int total = 0;
        for (Centre c : centres) {
            total += Math.max(0, screen.x() - (c.x() - radius)) + Math.max(0, c.x() + radius - screen.right())
                + Math.max(0, screen.y() - (c.y() - radius)) + Math.max(0, c.y() + radius - screen.bottom());
        }
        return total;
    }

    private static List<Centre> shiftInside(List<Centre> centres, Rect screen, int radius) {
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
        for (Centre c : centres) {
            left = Math.min(left, c.x() - radius);
            top = Math.min(top, c.y() - radius);
            right = Math.max(right, c.x() + radius);
            bottom = Math.max(bottom, c.y() + radius);
        }
        int dx = left < screen.x() ? screen.x() - left : right > screen.right() ? screen.right() - right : 0;
        int dy = top < screen.y() ? screen.y() - top : bottom > screen.bottom() ? screen.bottom() - bottom : 0;
        return centres.stream().map(c -> new Centre(c.x() + dx, c.y() + dy)).toList();
    }
}
