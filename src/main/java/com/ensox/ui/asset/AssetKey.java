package com.ensox.ui.asset;

import net.minecraft.resources.Identifier;

/**
 * Name of a UI asset in the {@link AssetLibrary}. Code refers to assets only
 * through keys; which file a key points to (and how it is drawn) is defined
 * once, in {@code assets/<namespace>/ui/assets.json}.
 *
 * <pre>
 * public static final AssetKey PANEL = AssetKey.of(MOD_ID, "panel/metal");
 * </pre>
 */
public record AssetKey(Identifier id) {
    public static AssetKey of(String namespace, String path) {
        return new AssetKey(Identifier.fromNamespaceAndPath(namespace, path));
    }

    /** {@code "namespace:path"}; a bare path uses the library default namespace. */
    public static AssetKey parse(String key) {
        return new AssetKey(key.indexOf(':') >= 0
            ? Identifier.parse(key)
            : Identifier.fromNamespaceAndPath(AssetLibrary.defaultNamespace(), key));
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
