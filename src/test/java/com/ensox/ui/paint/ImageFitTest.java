package com.ensox.ui.paint;

import com.ensox.ui.geom.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImageFitTest {
    private static final Rect PORTRAIT = Rect.of(20, 30, 92, 152);

    @Test void matchingAspectRatioNeedsNoCrop() {
        Rect area = Rect.of(12, 18, 90, 150);
        assertEquals(area, ImageFit.cover(area, 300, 500));
    }

    @Test void existingEditorCapturesFillThePortraitFrameWithOnlyMinorCropping() {
        Rect first = ImageFit.cover(PORTRAIT, 239, 384);
        Rect second = ImageFit.cover(PORTRAIT, 231, 384);
        assertEquals(Rect.of(18, 30, 95, 152), first);
        assertEquals(Rect.of(20, 29, 92, 153), second);
        assertEquals(PORTRAIT, first.intersect(PORTRAIT));
        assertEquals(PORTRAIT, second.intersect(PORTRAIT));
    }

    @Test void incompatibleRatiosFillTheFrameAndCropSymmetrically() {
        Rect landscape = ImageFit.cover(PORTRAIT, 384, 96);
        assertEquals(Rect.of(-238, 30, 608, 152), landscape);
        assertEquals(PORTRAIT, landscape.intersect(PORTRAIT));

        Rect tall = ImageFit.cover(PORTRAIT, 96, 384);
        assertEquals(Rect.of(20, -78, 92, 368), tall);
        assertEquals(PORTRAIT, tall.intersect(PORTRAIT));
    }

    @Test void smallImagesAreUpscaledToFillTheFrame() {
        Rect image = ImageFit.cover(PORTRAIT, 3, 5);
        assertEquals(Rect.of(20, 29, 92, 153), image);
        assertEquals(PORTRAIT, image.intersect(PORTRAIT));
    }

    @Test void centeredCoverageSurvivesPixelRoundingAndOddDimensions() {
        // Includes one-pixel images/areas and ratios on both sides of the fill threshold.
        int[] sizes = {1, 2, 31, 91, 92, 96, 151, 152, 156, 231, 239, 384};
        Rect[] areas = {PORTRAIT, Rect.of(7, 9, 152, 92), Rect.of(3, 4, 5, 9), Rect.of(1, 2, 1, 1)};
        for (Rect area : areas) {
            for (int width : sizes) {
                for (int height : sizes) {
                    Rect image = ImageFit.cover(area, width, height);
                    assertEquals(area, image.intersect(area));
                    assertTrue(image.width() == area.width() || image.height() == area.height());
                    assertTrue(Math.abs((image.x() - area.x()) - (area.right() - image.right())) <= 1);
                    assertTrue(Math.abs((image.y() - area.y()) - (area.bottom() - image.bottom())) <= 1);
                }
            }
        }
    }

    @Test void emptyAreasAndUnavailableImageDimensionsProduceNoDrawing() {
        assertTrue(ImageFit.cover(Rect.EMPTY, 239, 384).isEmpty());
        assertTrue(ImageFit.cover(Rect.of(0, 0, -1, 152), 239, 384).isEmpty());
        assertTrue(ImageFit.cover(PORTRAIT, 0, 384).isEmpty());
        assertTrue(ImageFit.cover(PORTRAIT, 239, -1).isEmpty());
    }
}
