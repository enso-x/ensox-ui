package com.ensox.ui.neoforge;

import com.ensox.ui.EnsoxUi;
import com.ensox.ui.asset.AssetReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

/** NeoForge client entry point: the library reloads its UI assets itself. */
@Mod(value = EnsoxUi.MOD_ID, dist = Dist.CLIENT)
public final class EnsoxUiNeoForge {
    public EnsoxUiNeoForge(IEventBus modBus) {
        modBus.addListener(EnsoxUiNeoForge::registerReloadListeners);
    }

    private static void registerReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(AssetReloadListener.ID, new AssetReloadListener());
    }
}
