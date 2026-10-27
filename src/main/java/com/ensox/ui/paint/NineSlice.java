package com.ensox.ui.paint;

import com.ensox.base.Insets;

/**
 * Nine-slice description of a texture region: corners are drawn 1:1, edges and
 * center are stretched or tiled to fill the target.
 *
 * <pre>
 * NineSlice.of(region, Insets.of(4))                 // stretch edges and center
 * NineSlice.of(region, Insets.of(4)).tiled()         // tile edges and center
 * </pre>
 */
public record NineSlice(TextureRegion region, Insets border, FillMode edges, FillMode center) {
    public enum FillMode {
        STRETCH,
        TILE
    }

    public static NineSlice of(TextureRegion region, Insets border) {
        return new NineSlice(region, border, FillMode.STRETCH, FillMode.STRETCH);
    }

    public NineSlice tiled() {
        return new NineSlice(region, border, FillMode.TILE, FillMode.TILE);
    }

    public NineSlice withEdges(FillMode mode) {
        return new NineSlice(region, border, mode, center);
    }

    public NineSlice withCenter(FillMode mode) {
        return new NineSlice(region, border, edges, mode);
    }
}
