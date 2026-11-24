package top.hachimi2333;

import top.hachimi2333.config.ClientConfig;
import top.hachimi2333.config.F3atherScreen;
import top.hachimi2333.overlay.AddonEntries;
import top.hachimi2333.overlay.OverlayHud;
import top.hachimi2333.overlay.OverlaySettings;
import top.hachimi2333.render.ModernFont;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

import java.util.function.Supplier;

@Mod(value = F3ather.MOD_ID, dist = Dist.CLIENT)
public final class F3ather {
    public static final String MOD_ID = "f3ather";
    public static final AddonEntries ADDONS = new AddonEntries();
    public static final OverlayHud HUD = new OverlayHud(ADDONS);

    public F3ather(IEventBus modBus, ModContainer container) {
        modBus.addListener((ModConfigEvent.Loading event) -> ClientConfig.reload(event));
        modBus.addListener((ModConfigEvent.Reloading event) -> ClientConfig.reload(event));
        modBus.addListener((RegisterClientReloadListenersEvent event) -> event.registerReloadListener(ModernFont.INSTANCE));
        NeoForge.EVENT_BUS.addListener((GameShuttingDownEvent event) -> ModernFont.INSTANCE.release());
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        Supplier<IConfigScreenFactory> factory = () -> (ignored, parent) -> new F3atherScreen(parent);
        container.registerExtensionPoint(IConfigScreenFactory.class, factory);
    }

    public static OverlaySettings settings() {
        return ClientConfig.current();
    }
}
