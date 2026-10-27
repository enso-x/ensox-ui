package com.ensox.ui.asset;

import com.ensox.base.Insets;
import com.ensox.ui.paint.NineSlice;
import com.ensox.ui.paint.TextureRegion;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parser for {@code assets/<namespace>/ui/assets.json}.
 *
 * <h2>Format</h2>
 * <pre>
 * {
 *   "panel/metal":  "sprite:surface/metal/sprite",
 *   "icon/*":       "sprite:icons/*",
 *   "frame/legacy": {
 *     "texture": "gui/legacy_frame",
 *     "size": [256, 256],
 *     "uv": [0, 0, 176, 166],
 *     "mode": "nine_slice",
 *     "border": "4 6",
 *     "center": "tile"
 *   }
 * }
 * </pre>
 *
 * <h2>Path rules</h2>
 * <ul>
 *     <li>Keys are relative to the file's namespace.</li>
 *     <li>{@code sprite} paths are GUI-atlas sprite ids, i.e. relative to
 *     {@code textures/gui/sprites/} without {@code .png}. Their scaling mode
 *     comes from the sprite {@code .mcmeta}.</li>
 *     <li>{@code texture} paths are relative to {@code textures/}; {@code .png}
 *     is optional. Their mode is defined here: {@code stretch} (default),
 *     {@code tile} or {@code nine_slice} with an Insets-shorthand {@code border}.</li>
 *     <li>Any path may carry its own namespace ({@code "minecraft:widget/button"}).</li>
 *     <li>A key ending in {@code /*} matches any key with that prefix; a
 *     {@code *} in its path is replaced by the matched rest.</li>
 * </ul>
 */
public final class AssetDefinitions {
    private static final String SPRITE_PREFIX = "textures/gui/sprites/";

    private AssetDefinitions() {
    }

    /** One parsed entry; {@code path} may still contain {@code *} for pattern keys. */
    public record Definition(Kind kind, String namespace, String path, int textureWidth, int textureHeight,
                             int u, int v, int width, int height,
                             UiAsset.Texture.Mode mode, Insets border,
                             NineSlice.FillMode edges, NineSlice.FillMode center) {
        public enum Kind {
            SPRITE,
            TEXTURE
        }

        UiAsset resolve(String wildcard) {
            Identifier resolved = Identifier.fromNamespaceAndPath(namespace,
                wildcard == null ? path : path.replace("*", wildcard));
            if (kind == Kind.SPRITE) {
                return new UiAsset.Sprite(resolved);
            }
            TextureRegion region = new TextureRegion(resolved, u, v, width, height, textureWidth, textureHeight);
            return new UiAsset.Texture(region, mode, new NineSlice(region, border, edges, center));
        }
    }

    public static Map<String, Definition> parse(String namespace, JsonObject json) {
        Map<String, Definition> definitions = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String key = entry.getKey();
            try {
                definitions.put(namespace + ":" + key, parseEntry(namespace, entry.getValue()));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Invalid UI asset '" + namespace + ":" + key + "': " + e.getMessage(), e);
            }
        }
        return definitions;
    }

    private static Definition parseEntry(String namespace, JsonElement value) {
        if (value.isJsonPrimitive()) {
            String text = value.getAsString();
            int colon = text.indexOf(':');
            if (colon < 0) {
                throw new IllegalArgumentException("expected 'sprite:<path>' or 'texture:<path>'");
            }
            String type = text.substring(0, colon);
            String path = text.substring(colon + 1);
            JsonObject object = new JsonObject();
            object.addProperty(type, path);
            return parseEntry(namespace, object);
        }
        JsonObject object = value.getAsJsonObject();
        if (object.has("sprite")) {
            String[] sprite = spriteId(namespace, object.get("sprite").getAsString());
            return new Definition(Definition.Kind.SPRITE, sprite[0], sprite[1],
                0, 0, 0, 0, 0, 0, UiAsset.Texture.Mode.STRETCH, Insets.none(),
                NineSlice.FillMode.STRETCH, NineSlice.FillMode.STRETCH);
        }
        if (!object.has("texture")) {
            throw new IllegalArgumentException("entry needs 'sprite' or 'texture'");
        }
        String[] texture = textureId(namespace, object.get("texture").getAsString());
        JsonArray size = object.getAsJsonArray("size");
        if (size == null || size.size() != 2) {
            throw new IllegalArgumentException("textures need 'size': [width, height]");
        }
        int textureWidth = size.get(0).getAsInt();
        int textureHeight = size.get(1).getAsInt();
        int u = 0;
        int v = 0;
        int w = textureWidth;
        int h = textureHeight;
        if (object.has("uv")) {
            JsonArray uv = object.getAsJsonArray("uv");
            u = uv.get(0).getAsInt();
            v = uv.get(1).getAsInt();
            w = uv.get(2).getAsInt();
            h = uv.get(3).getAsInt();
        }
        UiAsset.Texture.Mode mode = object.has("mode")
            ? UiAsset.Texture.Mode.valueOf(object.get("mode").getAsString().toUpperCase(Locale.ROOT))
            : UiAsset.Texture.Mode.STRETCH;
        Insets border = object.has("border") ? Insets.parse(object.get("border").getAsString()) : Insets.none();
        if (mode == UiAsset.Texture.Mode.NINE_SLICE && border.isNone()) {
            throw new IllegalArgumentException("nine_slice needs a 'border'");
        }
        NineSlice.FillMode center = fillMode(object, "center");
        NineSlice.FillMode edges = object.has("edges") ? fillMode(object, "edges") : center;
        return new Definition(Definition.Kind.TEXTURE, texture[0], texture[1], textureWidth, textureHeight, u, v, w, h,
            mode, border, edges, center);
    }

    private static NineSlice.FillMode fillMode(JsonObject object, String field) {
        return object.has(field)
            ? NineSlice.FillMode.valueOf(object.get(field).getAsString().toUpperCase(Locale.ROOT))
            : NineSlice.FillMode.STRETCH;
    }

    /**
     * Accepts sprite ids and, forgivingly, full texture paths of atlas sprites.
     * Returns {@code [namespace, path]}; the path may contain {@code *}, so it is
     * validated as an {@link Identifier} only when it has none.
     */
    static String[] spriteId(String namespace, String path) {
        String[] id = split(namespace, path);
        String p = id[1];
        if (p.startsWith(SPRITE_PREFIX)) {
            p = p.substring(SPRITE_PREFIX.length());
        }
        if (p.endsWith(".png")) {
            p = p.substring(0, p.length() - 4);
        }
        return validated(id[0], p);
    }

    static String[] textureId(String namespace, String path) {
        String[] id = split(namespace, path);
        String p = id[1];
        if (!p.startsWith("textures/")) {
            p = "textures/" + p;
        }
        if (!p.endsWith(".png")) {
            p = p + ".png";
        }
        return validated(id[0], p);
    }

    private static String[] split(String namespace, String path) {
        int colon = path.indexOf(':');
        return colon >= 0
            ? new String[]{path.substring(0, colon), path.substring(colon + 1)}
            : new String[]{namespace, path};
    }

    private static String[] validated(String namespace, String path) {
        // Throws with a clear message for invalid characters; '*' is checked after substitution.
        Identifier.fromNamespaceAndPath(namespace, path.replace("*", "x"));
        return new String[]{namespace, path};
    }
}
