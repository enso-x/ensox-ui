package com.ensox.ui.fabric;

import com.ensox.ui.asset.AssetReloadListener;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;

/** Fabric client entry point: the library reloads its UI assets itself. */
public final class EnsoxUiFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ResourceLoader.get(PackType.CLIENT_RESOURCES)
            .registerReloadListener(AssetReloadListener.ID, new AssetReloadListener());
    }
}
