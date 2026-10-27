package com.ensox.ui.asset;

import com.ensox.base.Insets;
import com.ensox.ui.paint.NineSlice;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssetDefinitionsTest {
    private static AssetDefinitions.Definition parseOne(String json) {
        return AssetDefinitions.parse("mod", JsonParser.parseString("{\"k\": " + json + "}").getAsJsonObject()).get("mod:k");
    }

    @Test
    void shortSpriteFormUsesFileNamespace() {
        UiAsset asset = parseOne("\"sprite:panel/metal\"").resolve(null);
        assertEquals(new UiAsset.Sprite(Identifier.fromNamespaceAndPath("mod", "panel/metal")), asset);
    }

    @Test
    void fullSpritePathsAreNormalisedToAtlasIds() {
        UiAsset asset = parseOne("\"sprite:other:textures/gui/sprites/x/y.png\"").resolve(null);
        assertEquals(new UiAsset.Sprite(Identifier.fromNamespaceAndPath("other", "x/y")), asset);
    }

    @Test
    void textureGetsPrefixSuffixRegionAndNineSlice() {
        var asset = assertInstanceOf(UiAsset.Texture.class, parseOne("""
            {"texture": "gui/frame", "size": [64, 32], "uv": [2, 4, 20, 10], "mode": "nine_slice", "border": "3 2", "center": "tile"}
            """).resolve(null));
        assertEquals(Identifier.fromNamespaceAndPath("mod", "textures/gui/frame.png"), asset.region().texture());
        assertEquals(20, asset.width());
        assertEquals(Insets.of(3, 2), asset.nineSlice().border());
        assertEquals(NineSlice.FillMode.TILE, asset.nineSlice().center());
        assertEquals(NineSlice.FillMode.TILE, asset.nineSlice().edges());
    }

    @Test
    void wildcardPathIsSubstituted() {
        var definitions = AssetDefinitions.parse("mod", JsonParser.parseString("{\"icon/*\": \"sprite:icons/*\"}").getAsJsonObject());
        UiAsset asset = definitions.get("mod:icon/*").resolve("cross");
        assertEquals(new UiAsset.Sprite(Identifier.fromNamespaceAndPath("mod", "icons/cross")), asset);
    }

    @Test
    void nineSliceWithoutBorderIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> parseOne("{\"texture\": \"a\", \"size\": [8, 8], \"mode\": \"nine_slice\"}"));
    }
}
