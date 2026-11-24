package top.hachimi2333.f3ather_fixture;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;

final class NetworkChecks {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PORT = Integer.getInteger("f3ather.fixture.host", 0);
    static final boolean HOST = PORT > 0;
    static final boolean GUEST = Boolean.getBoolean("f3ather.fixture.network");
    private static boolean hosting;
    private static int hostTicks;
    private static int guestTicks;
    private static ResourceKey<Level> initialDimension;
    private static boolean remoteSeen;
    private static boolean unloadedSeen;
    private static boolean changedDimension;
    private static boolean reducedSeen;

    private NetworkChecks() {}

    static void host(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (!hosting) {
            hosting = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.framerateLimit().set(60);
            server.setUsesAuthentication(false);
            try {
                server.getConnection().startTcpServerListener(InetAddress.getByName("127.0.0.1"), PORT);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            LOGGER.info("F3ATHER_NETWORK loopback listener ready on {}", PORT);
        }
        if (server.getPlayerCount() < 2) {
            if (hostTicks >= 300) {
                server.execute(() -> server.getGameRules().getRule(GameRules.RULE_REDUCEDDEBUGINFO).set(false, server));
                mc.stop();
            }
            return;
        }
        if (++hostTicks == 90) {
            server.execute(() -> {
                var guest = server.getPlayerList().getPlayerByName("FixtureGuest");
                if (guest == null) return;
                var target = guest.level().dimension() == Level.OVERWORLD ? Level.NETHER : Level.OVERWORLD;
                guest.teleportTo(server.getLevel(target), 0.5, 120, 0.5, 0, 0);
            });
        }
        if (hostTicks == 300) {
            server.execute(() -> server.getGameRules().getRule(GameRules.RULE_REDUCEDDEBUGINFO).set(true, server));
        }
    }

    static void guest(Minecraft mc) {
        if (initialDimension == null) {
            if (mc.screen != null) return;
            if (mc.getSingleplayerServer() != null) throw new IllegalStateException("Expected a remote connection");
            initialDimension = mc.level.dimension();
            mc.options.pauseOnLostFocus = false;
            mc.options.framerateLimit().set(60);
            FixtureChecks.prepare();
            FixtureChecks.enableAll();
        }
        if (guestTicks++ > 680) {
            throw new IllegalStateException("Network fixture timed out: remote=" + remoteSeen + ", unloaded="
                    + unloadedSeen + ", dimension=" + changedDimension + ", reduced=" + reducedSeen);
        }
        if (mc.screen != null) return;
        if (!mc.getDebugOverlay().showDebugScreen()) mc.getDebugOverlay().toggleOverlay();
        if (guestTicks == 8) {
            var camera = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
            var pos = BlockPos.containing(1_000_000, 120, 1_000_000);
            camera.setPos(pos.getX(), pos.getY(), pos.getZ());
            mc.setCameraEntity(camera);
        }
        if (guestTicks == 15) mc.setCameraEntity(mc.player);
        if (remoteSeen && unloadedSeen && changedDimension && reducedSeen) {
            LOGGER.info("F3ATHER_NETWORK passed remote statistics, unloaded chunk, dimension change and server-reduced rules");
            FixtureChecks.restore();
            mc.stop();
        }
    }

    static void observe(CustomizeGuiOverlayEvent.DebugText event, Minecraft mc) {
        if (!GUEST || initialDimension == null || mc.level == null) return;
        String dimension = mc.level.dimension().location().toString();
        for (String row : event.getLeft()) {
            if (row == null) continue;
            remoteSeen |= row.startsWith("\"") && row.contains("\" server");
            unloadedSeen |= row.equals("Waiting for chunk...");
            changedDimension |= mc.level.dimension() != initialDimension && row.startsWith(dimension + " FC: ");
        }
        if (mc.showOnlyReducedInfo()) {
            if (event.getLeft().stream().anyMatch(row -> row != null && row.startsWith("XYZ: "))) {
                throw new IllegalStateException("Server-restricted coordinates passed to DebugText");
            }
            reducedSeen = true;
        }
    }
}
