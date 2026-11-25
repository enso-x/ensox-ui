package com.ensox.ui.neoforge;

import com.ensox.ui.EnsoXUi;
import com.ensox.ui.asset.AssetReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

/** NeoForge client entry point: the library reloads its UI assets itself. */
@Mod(value = EnsoXUi.MOD_ID, dist = Dist.CLIENT)
public final class EnsoXUiNeoForge {
    public EnsoXUiNeoForge(IEventBus modBus) {
        modBus.addListener(EnsoXUiNeoForge::registerReloadListeners);
    }

    private static void registerReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(AssetReloadListener.ID, new AssetReloadListener());
    }
}
