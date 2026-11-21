package com.ensox.ui.paint;

import com.ensox.base.ColorX;
import com.ensox.base.Insets;
import com.ensox.ui.geom.Rect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaintTest {
    private static final Rect AREA = Rect.of(10, 20, 100, 80);
    private static final ColorX RED = ColorX.of(0xFFFF0000);
    private static final ColorX BLUE = ColorX.of(0xFF0000FF);

    @Test void coloredPaintsRemainIndependentWhenTheBaseIsReused() {
        Canvas parent = new Canvas(null, null);
        List<Draw> draws = new ArrayList<>();
        Paint base = probe("base", 0xFFAABBCC, draws);
        Paint red = base.color(RED);
        Paint blue = base.color(BLUE);

        red.paint(parent, AREA);
        base.paint(parent, AREA);
        blue.paint(parent, AREA);
        red.paint(parent, AREA);

        assertEquals(List.of(new Draw("base", AREA, 0xFFAA0000), new Draw("base", AREA, 0xFFAABBCC),
            new Draw("base", AREA, 0xFF0000CC), new Draw("base", AREA, 0xFFAA0000)), draws);
        assertEquals(0xFFAABBCC, parent.tintColor(0xFFAABBCC));
    }

    @Test void colorBeforeThenAffectsOnlyTheFirstLayer() {
        Canvas parent = new Canvas(null, null);
        List<Draw> draws = new ArrayList<>();
        Paint first = probe("first", 0xFFFFFFFF, draws);
        Paint second = probe("second", 0xFFFFFFFF, draws);

        first.color(RED).then(second).paint(parent, AREA);

        assertEquals(List.of(new Draw("first", AREA, 0xFFFF0000), new Draw("second", AREA, 0xFFFFFFFF)), draws);
    }

    @Test void colorAfterThenModulatesEveryLayerWithoutChangingTheirOrder() {
        Canvas parent = new Canvas(null, null);
        List<Draw> draws = new ArrayList<>();
        Paint first = probe("first", 0xFFFFFFFF, draws);
        Paint second = probe("second", 0xFF80FFFF, draws);
        Paint layers = first.then(second);

        layers.color(RED).paint(parent, AREA);
        layers.paint(parent, AREA);

        assertEquals(List.of(new Draw("first", AREA, 0xFFFF0000), new Draw("second", AREA, 0xFF800000),
            new Draw("first", AREA, 0xFFFFFFFF), new Draw("second", AREA, 0xFF80FFFF)), draws);
    }

    @Test void colorPreservesInsetGeometryAndDoesNotInsetSiblingLayers() {
        Canvas parent = new Canvas(null, null);
        List<Draw> draws = new ArrayList<>();
        Paint base = probe("base", 0xFFFFFFFF, draws);
        Insets insets = Insets.of(2, 3, 4, 7);

        base.inset(insets).color(RED).then(base).paint(parent, AREA);
        base.color(RED).inset(insets).paint(parent, AREA);
        base.inset(Insets.of(-2, 3, 4, -7)).color(BLUE).paint(parent, AREA);

        Rect inside = Rect.of(17, 22, 90, 74);
        assertEquals(List.of(new Draw("base", inside, 0xFFFF0000), new Draw("base", AREA, 0xFFFFFFFF),
            new Draw("base", inside, 0xFFFF0000), new Draw("base", Rect.of(3, 18, 104, 78), 0xFF0000FF)), draws);
    }

    @Test void canvasViewsMultiplyAllChannelsAndKeepTheirParentsUnchanged() {
        Canvas parent = new Canvas(null, null);
        ColorX source = ColorX.of(0x80C86432);
        Canvas first = parent.withColor(ColorX.of(0x80FF8080));
        Canvas nested = first.withColor(ColorX.of(0x80FFFFFF));

        // Half-opacity modulation also multiplies alpha; it does not replace the source alpha.
        assertEquals(0x40C83219, first.tintColor(source.toIntARGB()));
        assertEquals(0x20C83219, nested.tintColor(source.toIntARGB()));
        assertEquals(0x20C83219, nested.tintColor(source).toIntARGB());
        assertEquals(0x40C83219, first.tintColor(source).toIntARGB());
        assertEquals(0x80C86432, parent.tintColor(source.toIntARGB()));
        assertEquals(0x80C86432, source.toIntARGB());
    }

    @Test void nestedPaintColorMultipliesExistingCanvasModulationOnlyOnce() {
        Canvas parent = new Canvas(null, null).withColor(ColorX.of(0x80FFFFFF));
        List<Draw> draws = new ArrayList<>();
        Paint base = probe("base", 0x80C86432, draws);

        base.color(ColorX.of(0x80FF8080)).paint(parent, AREA);
        base.paint(parent, AREA);
        base.color(ColorX.of(0x80FF8080)).color(ColorX.of(0x80FFFFFF)).paint(new Canvas(null, null), AREA);

        assertEquals(List.of(new Draw("base", AREA, 0x20C83219), new Draw("base", AREA, 0x40C86432),
            new Draw("base", AREA, 0x20C83219)), draws);
    }

    @Test void aThrowingPaintCannotLeakItsColorToTheParentOrTheNextPaint() {
        Canvas parent = new Canvas(null, null).withColor(ColorX.of(0x80FFFFFF));
        List<Draw> draws = new ArrayList<>();
        IllegalStateException failure = new IllegalStateException("Paint failed");
        Paint broken = (canvas, area) -> {
            draws.add(new Draw("broken", area, canvas.tintColor(0xFFFFFFFF)));
            throw failure;
        };

        assertSame(failure, assertThrows(IllegalStateException.class, () -> broken.color(RED).paint(parent, AREA)));
        probe("next", 0xFFFFFFFF, draws).paint(parent, AREA);

        assertEquals(List.of(new Draw("broken", AREA, 0x80FF0000), new Draw("next", AREA, 0x80FFFFFF)), draws);
        assertEquals(0x80FFFFFF, parent.tintColor(0xFFFFFFFF));
    }

    @Test void whiteIsAnIdentityIncludingTransparentColorsAndAnAlreadyColoredCanvas() {
        Canvas parent = new Canvas(null, null);
        Canvas white = parent.withColor(ColorX.WHITE);
        for (int value : new int[] {0xFFFFFFFF, 0x00000000, 0x00123456, 0x80C86432, 0xFF000000}) {
            assertEquals(value, white.tintColor(value));
            assertEquals(value, white.tintColor(ColorX.of(value)).toIntARGB());
        }
        Canvas colored = parent.withColor(ColorX.of(0x80FF8080));
        assertEquals(0x40C83219, colored.withColor(ColorX.WHITE).tintColor(0x80C86432));

        List<Draw> draws = new ArrayList<>();
        Paint base = probe("base", 0x80C86432, draws);
        base.color(ColorX.WHITE).paint(colored, AREA);
        base.paint(colored, AREA);
        assertEquals(List.of(new Draw("base", AREA, 0x40C83219), new Draw("base", AREA, 0x40C83219)), draws);
    }

    /** Pure paint callback: observes the drawing contract without a Minecraft client or graphics context. */
    private static Paint probe(String name, int sourceColor, List<Draw> draws) {
        return (canvas, area) -> draws.add(new Draw(name, area, canvas.tintColor(sourceColor)));
    }

    private record Draw(String name, Rect area, int color) { }
}
