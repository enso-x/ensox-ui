package com.ensox.ui.paint;

import net.minecraft.resources.Identifier;

/**
 * A rectangle of a standalone texture file (not an atlas sprite), in texture
 * pixels. Atlas sprites are drawn by id instead and carry their scaling mode in
 * their {@code .mcmeta}.
 */
public record TextureRegion(Identifier texture, int u, int v, int width, int height,
                            int textureWidth, int textureHeight) {
    /** The whole texture. */
    public static TextureRegion of(Identifier texture, int textureWidth, int textureHeight) {
        return new TextureRegion(texture, 0, 0, textureWidth, textureHeight, textureWidth, textureHeight);
    }

    /** A sub-region of the same texture. */
    public TextureRegion region(int u, int v, int width, int height) {
        return new TextureRegion(texture, u, v, width, height, textureWidth, textureHeight);
    }
}
