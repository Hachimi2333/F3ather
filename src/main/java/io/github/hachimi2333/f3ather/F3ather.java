package io.github.hachimi2333.f3ather;

import io.github.hachimi2333.f3ather.config.ClientConfig;
import io.github.hachimi2333.f3ather.config.F3atherScreen;
import io.github.hachimi2333.f3ather.overlay.AddonEntries;
import io.github.hachimi2333.f3ather.overlay.OverlaySettings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.util.function.Supplier;

@Mod(value = F3ather.MOD_ID, dist = Dist.CLIENT)
public final class F3ather {
    public static final String MOD_ID = "f3ather";
    public static final AddonEntries ADDONS = new AddonEntries();

    public F3ather(IEventBus modBus, ModContainer container) {
        modBus.addListener((ModConfigEvent.Loading event) -> ClientConfig.reload(event));
        modBus.addListener((ModConfigEvent.Reloading event) -> ClientConfig.reload(event));
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        Supplier<IConfigScreenFactory> factory = () -> (ignored, parent) -> new F3atherScreen(parent, ADDONS);
        container.registerExtensionPoint(IConfigScreenFactory.class, factory);
    }

    public static OverlaySettings settings() {
        return ClientConfig.current();
    }
}
