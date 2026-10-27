package com.ensox.ui.asset;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Central registry of UI assets: key to file and draw mode.
 *
 * <p>Definitions come from {@code assets/<namespace>/ui/assets.json} in every
 * loaded resource pack. Packs are applied bottom to top, so a resource pack
 * can override single keys of a mod. See {@link AssetDefinitions} for the
 * file format and path rules.</p>
 *
 * <p>The host mod forwards resource reloads to {@link #reload}; after F3+T
 * every key resolves against the new files without touching code.</p>
 */
public final class AssetLibrary {
    private static final Logger LOGGER = LoggerFactory.getLogger("EnsoxUI/Assets");
    private static final Identifier FILE = Identifier.fromNamespaceAndPath("minecraft", "ui/assets.json");

    private static volatile Map<String, AssetDefinitions.Definition> exact = Map.of();
    /** Pattern prefixes (key without the trailing {@code *}), longest first. */
    private static volatile List<Map.Entry<String, AssetDefinitions.Definition>> patterns = List.of();
    private static final Map<AssetKey, UiAsset> resolved = new ConcurrentHashMap<>();
    private static final Set<AssetKey> reportedMissing = ConcurrentHashMap.newKeySet();
    private static String defaultNamespace = "minecraft";

    private AssetLibrary() {
    }

    /** Namespace used for bare keys ({@link AssetKey#parse}, icon names). Set once by the host mod. */
    public static void defaultNamespace(String namespace) {
        defaultNamespace = namespace;
    }

    public static String defaultNamespace() {
        return defaultNamespace;
    }

    public static UiAsset get(AssetKey key) {
        return resolved.computeIfAbsent(key, AssetLibrary::resolve);
    }

    /** Icon by short name: key {@code <default namespace>:icon/<name>}. */
    public static UiAsset icon(String name) {
        return get(AssetKey.of(defaultNamespace, "icon/" + name));
    }

    private static UiAsset resolve(AssetKey key) {
        String id = key.id().toString();
        AssetDefinitions.Definition definition = exact.get(id);
        if (definition != null) {
            return definition.resolve(null);
        }
        for (Map.Entry<String, AssetDefinitions.Definition> pattern : patterns) {
            if (id.startsWith(pattern.getKey())) {
                return pattern.getValue().resolve(id.substring(pattern.getKey().length()));
            }
        }
        if (reportedMissing.add(key)) {
            LOGGER.warn("UI asset '{}' is not defined in any ui/assets.json", key);
        }
        return new UiAsset.Missing(key);
    }

    public static CompletableFuture<Void> reload(ResourceManager resources, Executor prepareExecutor, Executor applyExecutor) {
        return CompletableFuture.supplyAsync(() -> load(resources), prepareExecutor)
            .thenAcceptAsync(AssetLibrary::apply, applyExecutor);
    }

    private static Map<String, AssetDefinitions.Definition> load(ResourceManager resources) {
        Map<String, AssetDefinitions.Definition> definitions = new LinkedHashMap<>();
        for (String namespace : resources.getNamespaces()) {
            Identifier file = Identifier.fromNamespaceAndPath(namespace, FILE.getPath());
            // Bottom pack first, so higher packs override per key.
            for (Resource resource : resources.getResourceStack(file)) {
                try (Reader reader = resource.openAsReader()) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    definitions.putAll(AssetDefinitions.parse(namespace, json));
                } catch (Exception e) {
                    LOGGER.error("Could not read {} from pack {}", file, resource.sourcePackId(), e);
                }
            }
        }
        return definitions;
    }

    private static void apply(Map<String, AssetDefinitions.Definition> definitions) {
        Map<String, AssetDefinitions.Definition> exactKeys = new HashMap<>();
        Map<String, AssetDefinitions.Definition> patternKeys = new HashMap<>();
        definitions.forEach((key, definition) -> {
            if (key.endsWith("*")) {
                patternKeys.put(key.substring(0, key.length() - 1), definition);
            } else {
                exactKeys.put(key, definition);
            }
        });
        exact = Map.copyOf(exactKeys);
        patterns = patternKeys.entrySet().stream()
            .sorted(Comparator.comparingInt((Map.Entry<String, AssetDefinitions.Definition> e) -> e.getKey().length()).reversed())
            .map(e -> Map.entry(e.getKey(), e.getValue()))
            .toList();
        resolved.clear();
        reportedMissing.clear();
        LOGGER.info("Loaded {} UI asset definitions ({} patterns)", exactKeys.size(), patternKeys.size());
    }

    /** Keys defined explicitly (patterns excluded), for diagnostics and tooling. */
    public static Set<String> definedKeys() {
        return new HashSet<>(exact.keySet());
    }
}
