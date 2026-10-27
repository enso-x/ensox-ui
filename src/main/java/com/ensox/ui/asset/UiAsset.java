package com.ensox.ui.asset;

import com.ensox.ui.geom.Rect;
import com.ensox.ui.paint.Canvas;
import com.ensox.ui.paint.NineSlice;
import com.ensox.ui.paint.TextureRegion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;

/** A resolved library entry: something drawable with a natural size. */
public sealed interface UiAsset permits UiAsset.Sprite, UiAsset.Texture, UiAsset.Missing {
    void paint(Canvas canvas, Rect rect, int tintArgb);

    /** Natural width in GUI pixels (used by content-sized icons). */
    int width();

    int height();

    /**
     * GUI-atlas sprite ({@code textures/gui/sprites/...}). Stretch, tile or
     * nine-slice comes from the sprite {@code .mcmeta}, as for vanilla GUI sprites.
     */
    record Sprite(Identifier sprite) implements UiAsset {
        @Override
        public void paint(Canvas canvas, Rect rect, int tintArgb) {
            canvas.sprite(sprite, rect, tintArgb);
        }

        public TextureAtlasSprite atlasSprite() {
            return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.GUI).getSprite(sprite);
        }

        @Override
        public int width() {
            return atlasSprite().contents().width();
        }

        @Override
        public int height() {
            return atlasSprite().contents().height();
        }
    }

    /** Standalone texture file with its draw mode defined in the library. */
    record Texture(TextureRegion region, Mode mode, NineSlice nineSlice) implements UiAsset {
        public enum Mode {
            STRETCH,
            TILE,
            NINE_SLICE
        }

        @Override
        public void paint(Canvas canvas, Rect rect, int tintArgb) {
            switch (mode) {
                case STRETCH -> canvas.texture(region, rect, tintArgb);
                case TILE -> canvas.tiled(region, rect, tintArgb);
                case NINE_SLICE -> canvas.nineSlice(nineSlice, rect, tintArgb);
            }
        }

        @Override
        public int width() {
            return region.width();
        }

        @Override
        public int height() {
            return region.height();
        }
    }

    /** Placeholder for unknown keys: visible on purpose so missing entries are noticed. */
    record Missing(AssetKey key) implements UiAsset {
        @Override
        public void paint(Canvas canvas, Rect rect, int tintArgb) {
            canvas.fill(rect, 0xFFF800F8);
            canvas.outline(rect, 0xFF000000);
        }

        @Override
        public int width() {
            return 16;
        }

        @Override
        public int height() {
            return 16;
        }
    }
}
