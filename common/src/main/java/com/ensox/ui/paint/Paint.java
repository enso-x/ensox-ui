package com.ensox.ui.paint;

import com.ensox.base.ColorX;
import com.ensox.base.Insets;
import com.ensox.ui.asset.AssetKey;
import com.ensox.ui.geom.Rect;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;

/**
 * Something that fills a rectangle: a background, a frame, an overlay.
 *
 * <p>Paints are values. Build them once (usually as theme constants) and reuse
 * them across widgets; combine them with {@link #then(Paint)} and
 * {@link #inset(Insets)} and {@link #color(ColorX)} instead of writing new drawing code:</p>
 * <pre>
 * Paint panel = Paint.asset(Textures.PANEL_FRAME)
 *     .color(ColorX.of("#7BFFFB"))
 *     .then(Paint.fill(0x80000000).inset(Insets.of(3)));
 * </pre>
 *
 * <p>Applications add their own effects (blur, shaders) by implementing this
 * interface; the UI core never needs to know about them.</p>
 */
@FunctionalInterface
public interface Paint {
    Paint NONE = (canvas, area) -> {
    };

    void paint(Canvas canvas, Rect area);

    static Paint none() {
        return NONE;
    }

    /** A solid fill; use {@link #color(ColorX)} to tint an existing paint. */
    static Paint fill(int argb) {
        return (canvas, area) -> canvas.fill(area, argb);
    }

    static Paint fill(ColorX color) {
        return fill(color.toIntARGB());
    }

    static Paint verticalGradient(ColorX top, ColorX bottom) {
        int topArgb = top.toIntARGB();
        int bottomArgb = bottom.toIntARGB();
        return (canvas, area) -> canvas.verticalGradient(area, topArgb, bottomArgb);
    }

    static Paint outline(ColorX color) {
        int argb = color.toIntARGB();
        return (canvas, area) -> canvas.outline(area, argb);
    }

    /** GUI-atlas sprite; scaling mode from its {@code .mcmeta}. */
    static Paint sprite(Identifier sprite) {
        return (canvas, area) -> canvas.sprite(sprite, area);
    }

    static Paint sprite(Identifier sprite, ColorX tint) {
        return sprite(sprite).color(tint);
    }

    /** Library asset by key; the file and draw mode live in {@code ui/assets.json}. */
    static Paint asset(AssetKey key) {
        return (canvas, area) -> canvas.asset(key, area);
    }

    static Paint asset(AssetKey key, ColorX tint) {
        return asset(key).color(tint);
    }

    static Paint texture(TextureRegion region) {
        return (canvas, area) -> canvas.texture(region, area, -1);
    }

    static Paint tiled(TextureRegion region) {
        return (canvas, area) -> canvas.tiled(region, area, -1);
    }

    static Paint nineSlice(NineSlice slice) {
        return (canvas, area) -> canvas.nineSlice(slice, area, -1);
    }

    /** Draws the paints in order, each over the previous one. */
    static Paint layers(Paint... paints) {
        List<Paint> list = List.of(paints);
        return (canvas, area) -> {
            for (Paint paint : list) {
                paint.paint(canvas, area);
            }
        };
    }

    /** This paint, then {@code over} on top. */
    default Paint then(Paint over) {
        return layers(this, over);
    }

    /** Paints into the area shrunk by {@code insets} (negative values grow it). */
    default Paint inset(Insets insets) {
        return (canvas, area) -> paint(canvas, area.inset(insets));
    }

    /**
     * Multiplies this paint's RGBA colors without modifying the original paint or canvas.
     * Applied after {@link #then(Paint)}, it tints every layer; applied before it, only this layer.
     * Repeated calls multiply their tints. Custom render states must use {@link Canvas#tintColor(int)};
     * direct graphics access and item rendering are outside this color decorator.
     */
    default Paint color(ColorX tint) {
        Objects.requireNonNull(tint, "tint");
        if (tint.toIntARGB() == -1) return this;
        return (canvas, area) -> paint(canvas.withColor(tint), area);
    }
}
