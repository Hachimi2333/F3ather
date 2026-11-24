package top.hachimi2333.f3ather_fixture_peer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "f3ather_fixture_peer", dist = Dist.CLIENT)
public final class FixturePeer {
    public FixturePeer() {
        NeoForge.EVENT_BUS.addListener(FixturePeer::addDebugText);
    }

    private static void addDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        event.getLeft().add("Shared status: ready");
    }
}
