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

    /**
     * Icon by short name: {@code <namespace>:icon/<name>}. There is no library-wide default
     * namespace (several mods share the library), so callers name theirs.
     */
    public static AssetKey icon(String namespace, String name) {
        return of(namespace, "icon/" + name);
    }

    /** {@code "namespace:path"}; a bare path uses {@code defaultNamespace}. */
    public static AssetKey parse(String key, String defaultNamespace) {
        return new AssetKey(key.indexOf(':') >= 0
            ? Identifier.parse(key)
            : Identifier.fromNamespaceAndPath(defaultNamespace, key));
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
