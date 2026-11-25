package com.ensox.ui.paint;

import com.ensox.ui.geom.Rect;

/** Aspect-preserving image placement, independent of textures and rendering state. */
public final class ImageFit {
    private ImageFit() {}

    /**
     * Centers and proportionally scales the image to cover the entire area.
     * The caller must clip drawing to {@code area}, like CSS object-fit: cover
     * with object-position: center center.
     */
    public static Rect cover(Rect area, int imageWidth, int imageHeight) {
        if (area.isEmpty() || imageWidth <= 0 || imageHeight <= 0) {
            return Rect.of(area.x(), area.y(), 0, 0);
        }

        double scaleX = area.width() / (double) imageWidth;
        double scaleY = area.height() / (double) imageHeight;
        double scale = Math.max(scaleX, scaleY);

        // Round to GUI pixels while keeping both dimensions large enough to cover the area.
        int width = (int) Math.min(Integer.MAX_VALUE, Math.max(area.width(), Math.round(imageWidth * scale)));
        int height = (int) Math.min(Integer.MAX_VALUE, Math.max(area.height(), Math.round(imageHeight * scale)));
        return Rect.of(area.x() + Math.floorDiv(area.width() - width, 2),
            area.y() + Math.floorDiv(area.height() - height, 2), width, height);
    }
}
