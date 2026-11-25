package com.ensox.ui.widget;

import com.ensox.base.ColorX;
import com.ensox.ui.geom.Rect;
import com.ensox.ui.paint.Canvas;
import com.ensox.ui.asset.AssetKey;

/** Look of a {@link ScrollView} scrollbar. */
public interface ScrollbarStyle {
    /**
     * @param handle     handle rectangle
     * @param track      full track rectangle
     * @param vertical   orientation
     * @param visibility 0..1; 1 while the user interacts, fading to 0 afterwards
     */
    void paint(Canvas canvas, Rect handle, Rect track, boolean vertical, float visibility);

    /** Space the scrollbar takes from the viewport while scrolling is possible; 0 to overlay content. */
    default int reservedSpace(int thickness) {
        return 0;
    }

    /** Overlaid flat handle that fades out when idle. */
    static ScrollbarStyle flat(ColorX color) {
        int argb = color.toIntARGB();
        return (canvas, handle, track, vertical, visibility) -> {
            int alpha = (int) (visibility * (argb >>> 24));
            if (alpha > 0) {
                canvas.fill(handle, alpha << 24 | (argb & 0xFFFFFF));
            }
        };
    }

    /** Track and handle sprites; the track takes space next to the content. Handle is inset by 1px. */
    static ScrollbarStyle textured(AssetKey track, AssetKey handle) {
        return new ScrollbarStyle() {
            @Override
            public void paint(Canvas canvas, Rect handleRect, Rect trackRect, boolean vertical, float visibility) {
                canvas.asset(track, trackRect);
                canvas.asset(handle, Rect.of(handleRect.x() + 1, handleRect.y() + 1, handleRect.width() - 2, handleRect.height() - 2));
            }

            @Override
            public int reservedSpace(int thickness) {
                return thickness;
            }
        };
    }
}
