package com.ensox.ui.layout;

import com.ensox.ui.geom.Rect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RadialLayoutTest {
    private static final RadialLayout.Spec SPEC = new RadialLayout.Spec(11, 3, 7, 150);
    private static final Rect SCREEN = Rect.of(0, 0, 800, 600);

    @Test void arcGrowsOnlyWhenTheButtonsNoLongerFit() {
        double[] expected = {18, 18, 20.5, 29.6, 38.9, 48.3};
        for (int n = 1; n <= 6; n++) {
            assertEquals(expected[n - 1], RadialLayout.arcRadius(n, SPEC), 0.05, "radius for " + n);
        }
    }

    @Test void neighboursNeverOverlapAndTheArcOpensUpward() {
        for (int n = 1; n <= 8; n++) {
            RadialLayout.Placement placement = RadialLayout.place(n, 400, 300, SCREEN, SPEC);
            List<RadialLayout.Centre> centres = placement.centres();
            assertEquals(n, centres.size());
            for (int i = 1; i < n; i++) {
                // Rounding to whole pixels may take one pixel off the gap, never more.
                assertTrue(distance(centres.get(i - 1), centres.get(i)) >= 2 * 11 + 3 - 1.5, "gap for " + n);
            }
            for (RadialLayout.Centre c : centres) {
                assertTrue(distance(c, new RadialLayout.Centre(400, 300)) >= 11 + 7 - 1, "clear of the click");
            }
            double meanY = centres.stream().mapToInt(RadialLayout.Centre::y).average().orElseThrow();
            assertTrue(meanY < 300, "opens upward in free space");
        }
    }

    @Test void theArcTurnsAtScreenEdgesInsteadOfLeavingIt() {
        assertInside(RadialLayout.place(3, 400, 5, SCREEN, SPEC));
        double meanY = RadialLayout.place(3, 400, 5, SCREEN, SPEC).centres().stream()
            .mapToInt(RadialLayout.Centre::y).average().orElseThrow();
        assertTrue(meanY > 5, "turns down at the top edge");
        assertInside(RadialLayout.place(3, 795, 300, SCREEN, SPEC));
        assertInside(RadialLayout.place(3, 4, 300, SCREEN, SPEC));
    }

    private static void assertInside(RadialLayout.Placement placement) {
        for (RadialLayout.Centre c : placement.centres()) {
            assertTrue(c.x() - 11 >= 0 && c.x() + 11 <= 800 && c.y() - 11 >= 0 && c.y() + 11 <= 600, c.toString());
        }
    }

    private static double distance(RadialLayout.Centre a, RadialLayout.Centre b) {
        return Math.hypot(a.x() - b.x(), a.y() - b.y());
    }
}
