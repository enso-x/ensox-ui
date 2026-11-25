package com.ensox.ui.asset;

import com.ensox.ui.EnsoxUi;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Client resource reload of the {@link AssetLibrary}. The loader entry points register exactly one
 * instance, so the library reloads once however many mods use it, and also when no mod forwards
 * reloads (a mod that only ships {@code ui/assets.json}).
 */
public final class AssetReloadListener implements PreparableReloadListener {
    public static final Identifier ID = EnsoxUi.id("assets");

    @Override
    public CompletableFuture<Void> reload(SharedState sharedState, Executor prepareExecutor,
                                          PreparationBarrier barrier, Executor applyExecutor) {
        return AssetLibrary.reload(sharedState.resourceManager(), prepareExecutor, barrier, applyExecutor);
    }
}
