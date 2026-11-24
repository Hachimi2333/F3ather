package top.hachimi2333.overlay;

import com.mojang.blaze3d.platform.GlUtil;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

final class DebugData {
    private static final long FAST_NANOS = 100_000_000L;
    private static final long SLOW_NANOS = 1_000_000_000L;
    private static final long MEBIBYTE = 1L << 20;

    private final Map<VanillaEntry, List<String>> values = new EnumMap<>(VanillaEntry.class);
    private ClientLevel lastLevel;
    private Entity lastCamera;
    private Object language;
    private OverlaySettings lastSettings;
    private boolean lastReduced;
    private int lastCharts;
    private long nextFast;
    private long nextSlow;
    private long heapAt;
    private long lastHeap;
    private ChunkPos lastServerPos;
    private CompletableFuture<LevelChunk> serverChunk;
    private CompletableFuture<Integer> forcedChunks;
    private long nextForcedChunks;
    private int forcedChunkCount = -1;

    void invalidate() {
        lastLevel = null;
        lastCamera = null;
        values.clear();
        nextFast = 0;
        nextSlow = 0;
        heapAt = 0;
        lastServerPos = null;
        serverChunk = null;
        forcedChunks = null;
        nextForcedChunks = 0;
        forcedChunkCount = -1;
    }

    Map<VanillaEntry, List<String>> sample(Minecraft mc, OverlaySettings settings, int charts) {
        var level = mc.level;
        var camera = mc.getCameraEntity();
        var lang = net.minecraft.locale.Language.getInstance();
        boolean reduced = mc.showOnlyReducedInfo();
        if (lastLevel != level || lastCamera != camera || language != lang || lastSettings != settings
                || lastReduced != reduced || lastCharts != charts) {
            invalidate();
            lastLevel = level;
            lastCamera = camera;
            language = lang;
            lastSettings = settings;
            lastReduced = reduced;
            lastCharts = charts;
        }
        if (level == null || camera == null || mc.player == null) return values;
        long now = System.nanoTime();
        boolean fast = nextFast == 0 || now >= nextFast;
        boolean slow = nextSlow == 0 || now >= nextSlow;
        if (!fast && !slow) return values;
        var context = new Context(mc, level, camera, charts);
        ProfilerFiller profiler = mc.getProfiler();
        profiler.push("f3ather_sample");
        try {
            for (VanillaEntry entry : VanillaEntry.ALL) {
                if ((entry.slow() ? slow : fast) && settings.enabled().contains(entry.id())
                        && (!reduced || entry.allowedInReduced())) {
                    values.put(entry, read(entry, context, now));
                }
            }
        } finally {
            profiler.pop();
        }
        if (fast) nextFast = now + FAST_NANOS;
        if (slow) nextSlow = now + SLOW_NANOS;
        return values;
    }

    private List<String> read(VanillaEntry entry, Context c, long now) {
        Minecraft mc = c.mc;
        BlockPos pos = c.pos;
        var chunk = c.chunk;
        ClientLevel level = c.level;
        var options = mc.options;
        Entity camera = c.camera;
        return switch (entry) {
            case FPS -> value(entry, tr("fps", mc.getFps(), decimal(mc.getFrameTimeNs() / 1e6, 1)));
            case GRAPHICS -> value(entry, tr("graphics",
                    options.framerateLimit().get() == 260 ? "∞" : options.framerateLimit().get(),
                    status(options.enableVsync().get()), tr("graphics." + options.graphicsMode().get().name().toLowerCase(Locale.ROOT)),
                    tr("clouds." + options.cloudStatus().get().name().toLowerCase(Locale.ROOT)),
                    options.biomeBlendRadius().get(), mc.getEntityRenderDispatcher().shouldRenderHitBoxes() ? tr("hitboxes") : ""));
            case XYZ -> value(entry, xyz(decimal(camera.getX(), 3), decimal(camera.getY(), 5), decimal(camera.getZ(), 3)));
            case CHUNK_RELATIVE -> value(entry, xyz(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15));
            case BLOCK_POSITION -> value(entry, xyz(pos.getX(), pos.getY(), pos.getZ()));
            case CHUNK_POSITION -> value(entry, tr("chunk", xyz(chunk.x, SectionPos.blockToSectionCoord(pos.getY()), chunk.z),
                    chunk.getRegionLocalX(), chunk.getRegionLocalZ(), chunk.getRegionX(), chunk.getRegionZ(),
                    c.clientChunk() == null ? tr("waiting_chunk") : tr("loaded")));
            case FACING -> value(entry, tr("facing", tr("direction." + camera.getDirection().getName()),
                    camera.getDirection().getAxisDirection().getStep() > 0 ? "+" : "−",
                    camera.getDirection().getAxis().getName().toUpperCase(Locale.ROOT),
                    decimal(Mth.wrapDegrees(camera.getYRot()), 1), decimal(Mth.wrapDegrees(camera.getXRot()), 1)));
            case BIOME -> level.isOutsideBuildHeight(pos) || c.clientChunk() == null ? List.of()
                    : value(entry, level.getBiome(pos).unwrapKey().map(ResourceKey::location).map(id ->
                            translatedName("biome." + id.getNamespace() + "." + id.getPath(), id.toString())).orElse(tr("unavailable")));
            case DIMENSION -> value(entry, tr("dimension", level.dimension().location().toString(),
                    forcedChunkCount(c, now)));
            case LIGHT_LEVEL -> c.clientChunk() == null ? value(entry, tr("waiting_chunk")) : value(entry, tr("light",
                    level.getChunkSource().getLightEngine().getRawBrightness(pos, 0),
                    level.getBrightness(LightLayer.SKY, pos), level.getBrightness(LightLayer.BLOCK, pos)));
            case TARGET_BLOCK -> target(entry, c, false);
            case TARGET_FLUID -> target(entry, c, true);
            case TARGET_ENTITY -> targetEntity(mc);
            case VERSION -> value(entry, tr("version", SharedConstants.getCurrentVersion().getName(), mc.getLaunchedVersion(),
                    ClientBrandRetriever.getClientModName(), mc.getVersionType()));
            case SERVER -> server(c);
            case RENDERED_CHUNKS -> value(entry, tr("sections", mc.levelRenderer.countRenderedSections(),
                    (int) mc.levelRenderer.getTotalSections(), (int) mc.levelRenderer.getLastViewDistance(),
                    status(mc.smartCull), mc.levelRenderer.getSectionRenderDispatcher() == null
                            ? tr("unavailable") : mc.levelRenderer.getSectionRenderDispatcher().getStats()));
            case RENDERED_ENTITIES -> {
                String stats = mc.levelRenderer.getEntityStatistics();
                yield value(entry, stats.startsWith("E: ") ? stats.substring(3)
                        .replace(", B: ", " · " + tr("culled") + " ").replace(", SD: ", " · " + tr("simulation") + " ") : stats);
            }
            case PARTICLES -> value(entry, tr("particles", mc.particleEngine.countParticles(), level.getEntityCount()));
            case CLIENT_CHUNKS -> value(entry, tr("client_chunks", level.getChunkSource().getLoadedChunksCount(),
                    level.gatherChunkSourceStats().split(",", 2)[0]));
            case SERVER_CHUNKS -> c.server == null ? List.of() : value(entry, c.server.getChunkSource().getLoadedChunksCount());
            case HEIGHTMAPS -> heightmaps(c);
            case DIFFICULTY -> {
                if (level.isOutsideBuildHeight(pos) || c.clientChunk() == null) yield List.of();
                var server = serverChunk(c);
                if (server == null) yield value(entry, tr("unavailable"));
                var difficulty = new DifficultyInstance(c.server.getDifficulty(), c.server.getDayTime(),
                        server.getInhabitedTime(), c.server.getMoonBrightness());
                yield value(entry, tr("difficulty", decimal(difficulty.getEffectiveDifficulty(), 2),
                        decimal(difficulty.getSpecialMultiplier(), 2), level.getDayTime() / 24000L));
            }
            case BLENDING -> {
                LevelChunk server = serverChunk(c);
                yield server != null && server.isOldNoiseGeneration() ? value(entry, tr("old_noise")) : List.of();
            }
            case WORLD_GENERATION -> generation(c);
            case SPAWNING -> spawning(c);
            case POST_EFFECT -> mc.gameRenderer.currentEffect() == null ? List.of()
                    : value(entry, mc.gameRenderer.currentEffect().getName());
            case SOUND -> {
                String sound = mc.getSoundManager().getDebugString();
                yield value(entry, tr("sound", sound.startsWith("Sounds: ") ? sound.substring(8) : sound,
                        Math.round(mc.player.getCurrentMood() * 100)));
            }
            case DEBUG_CHART_HINT -> value(entry, tr("charts", status((c.charts & 1) != 0),
                    tr(mc.isLocalServer() ? "fps_tps" : "fps_only"), status((c.charts & 2) != 0),
                    tr(mc.isLocalServer() ? "ping_only" : "bandwidth_ping"), status((c.charts & 4) != 0)));
            case DEBUG_HELP -> value(entry, "F3 + Q");
            case DEMO -> {
                long remaining = 120500L - level.getGameTime();
                yield !mc.isDemo() ? List.of() : value(entry, remaining <= 0
                        ? Component.translatable("demo.demoExpired").getString()
                        : Component.translatable("demo.remainingTime",
                                StringUtil.formatTickDuration((int) remaining, level.tickRateManager().tickrate())).getString());
            }
            case JAVA -> value(entry, System.getProperty("java.version"));
            case MEMORY -> value(entry, tr("memory", usedHeap() / MEBIBYTE, Runtime.getRuntime().maxMemory() / MEBIBYTE,
                    usedHeap() * 100 / Runtime.getRuntime().maxMemory()));
            case ALLOCATION_RATE -> {
                long heap = usedHeap();
                long rate = heapAt == 0 ? 0 : Math.max(0, heap - lastHeap) * SLOW_NANOS / (now - heapAt) / MEBIBYTE;
                lastHeap = heap;
                heapAt = now;
                yield value(entry, tr("allocation", rate));
            }
            case ALLOCATED -> value(entry, tr("allocated", Runtime.getRuntime().totalMemory() / MEBIBYTE,
                    Runtime.getRuntime().totalMemory() * 100 / Runtime.getRuntime().maxMemory()));
            case CPU -> value(entry, GlUtil.getCpuInfo());
            case DISPLAY -> value(entry, tr("display", mc.getWindow().getWidth(), mc.getWindow().getHeight(), GlUtil.getVendor()));
            case GPU -> value(entry, GlUtil.getRenderer());
            case OPENGL -> value(entry, GlUtil.getOpenGLVersion());
        };
    }

    private static List<String> server(Context c) {
        var packet = c.mc.getConnection();
        if (packet == null) return List.of();
        var connection = packet.getConnection();
        var tick = c.server == null ? c.level.tickRateManager() : c.server.tickRateManager();
        String state = tick.isSteppingForward() ? tr("stepping") : tick.isFrozen() ? tr("frozen") : "";
        String sent = decimal(connection.getAverageSentPackets(), 0);
        String received = decimal(connection.getAverageReceivedPackets(), 0);
        if (c.integrated != null) {
            boolean sprint = c.integrated.tickRateManager().isSprinting();
            return value(VanillaEntry.SERVER, tr("integrated", decimal(c.integrated.getCurrentSmoothedTickTime(), 1),
                    sprint ? "–" : decimal(tick.millisecondsPerTick(), 1), sent, received, sprint ? tr("sprinting") : state));
        }
        return value(VanillaEntry.SERVER, tr("remote", packet.serverBrand(), sent, received, state));
    }

    private List<String> heightmaps(Context c) {
        LevelChunk client = c.clientChunk();
        if (client == null) return value(VanillaEntry.HEIGHTMAPS, tr("waiting_chunk"));
        LevelChunk server = serverChunk(c);
        var clientHeights = new ArrayList<String>();
        var serverHeights = new ArrayList<String>();
        for (var type : Heightmap.Types.values()) {
            String label = tr("height." + type.name().toLowerCase(Locale.ROOT));
            if (type.sendToClient()) clientHeights.add(label + " " + client.getHeight(type, c.pos.getX(), c.pos.getZ()));
            if (type.keepAfterWorldgen()) serverHeights.add(label + " " + (server == null ? "–"
                    : server.getHeight(type, c.pos.getX(), c.pos.getZ())));
        }
        return List.of(line(VanillaEntry.HEIGHTMAPS.translationKey(), tr("height_client", String.join(" · ", clientHeights))),
                tr("height_server", String.join(" · ", serverHeights)));
    }

    private LevelChunk serverChunk(Context c) {
        if (c.server == null) return null;
        if (!c.chunk.equals(lastServerPos) || serverChunk == null) {
            lastServerPos = c.chunk;
            serverChunk = c.server.getChunkSource().getChunkFuture(c.chunk.x, c.chunk.z, ChunkStatus.FULL, false)
                    .thenApply(result -> (LevelChunk) result.orElse(null));
        }
        if (serverChunk.isCompletedExceptionally()) {
            serverChunk = null;
            return null;
        }
        LevelChunk chunk = serverChunk.getNow(null);
        if (chunk == null && serverChunk.isDone()) serverChunk = null;
        return chunk;
    }

    private Object forcedChunkCount(Context c, long now) {
        if (c.server == null) return "–";
        if (forcedChunks != null && forcedChunks.isDone()) {
            if (!forcedChunks.isCompletedExceptionally()) forcedChunkCount = forcedChunks.getNow(-1);
            forcedChunks = null;
        }
        if (forcedChunks == null && now >= nextForcedChunks) {
            // Saved data may need to load from disk. Request it on the world thread.
            forcedChunks = c.integrated.submit(() -> c.server.getForcedChunks().size());
            nextForcedChunks = now + SLOW_NANOS;
        }
        return forcedChunkCount < 0 ? "–" : forcedChunkCount;
    }

    private static List<String> generation(Context c) {
        if (c.server == null) return List.of();
        var cache = c.server.getChunkSource();
        var generator = cache.getGenerator();
        var state = cache.randomState();
        var rows = new ArrayList<String>();
        if (generator instanceof NoiseBasedChunkGenerator) {
            var router = state.router();
            var point = new DensityFunction.SinglePointContext(c.pos.getX(), c.pos.getY(), c.pos.getZ());
            double ridges = router.ridges().compute(point);
            rows.add(tr("noise", decimal(router.temperature().compute(point), 3), decimal(router.vegetation().compute(point), 3),
                    decimal(router.continents().compute(point), 3), decimal(router.erosion().compute(point), 3),
                    decimal(router.depth().compute(point), 3), decimal(ridges, 3),
                    decimal(NoiseRouterData.peaksAndValleys((float) ridges), 3),
                    decimal(router.initialDensityWithoutJaggedness().compute(point), 3), decimal(router.finalDensity().compute(point), 3)));
        } else {
            generator.addDebugScreenInfo(rows, state, c.pos);
        }
        if (generator.getBiomeSource() instanceof MultiNoiseBiomeSource) {
            var sample = state.sampler().sample(QuartPos.fromBlock(c.pos.getX()), QuartPos.fromBlock(c.pos.getY()), QuartPos.fromBlock(c.pos.getZ()));
            var builder = new OverworldBiomeBuilder();
            rows.add(tr("climate", localeBin(OverworldBiomeBuilder.getDebugStringForPeaksAndValleys(
                            NoiseRouterData.peaksAndValleys(Climate.unquantizeCoord(sample.weirdness())))),
                    localeBin(builder.getDebugStringForContinentalness(Climate.unquantizeCoord(sample.continentalness()))),
                    builder.getDebugStringForErosion(Climate.unquantizeCoord(sample.erosion())),
                    builder.getDebugStringForTemperature(Climate.unquantizeCoord(sample.temperature())),
                    builder.getDebugStringForHumidity(Climate.unquantizeCoord(sample.humidity()))));
        } else {
            generator.getBiomeSource().addDebugInfo(rows, c.pos, state.sampler());
        }
        if (!rows.isEmpty()) rows.set(0, line(VanillaEntry.WORLD_GENERATION.translationKey(), rows.getFirst()));
        return rows;
    }

    private static String localeBin(String name) {
        return translatedName("f3ather.value.bin." + name.toLowerCase(Locale.ROOT).replace(' ', '_'), name);
    }

    private static List<String> spawning(Context c) {
        if (c.server == null) return List.of();
        var spawn = c.server.getChunkSource().getLastSpawnState();
        if (spawn == null) return value(VanillaEntry.SPAWNING, tr("unavailable"));
        var counts = new ArrayList<String>();
        for (MobCategory category : MobCategory.values()) {
            counts.add(tr("mob." + category.getName()) + " " + spawn.getMobCategoryCounts().getInt(category));
        }
        return value(VanillaEntry.SPAWNING, tr("spawning", spawn.getSpawnableChunkCount(), String.join(" · ", counts)));
    }

    private static List<String> target(VanillaEntry entry, Context c, boolean fluid) {
        HitResult hit = c.camera.pick(20, 0, fluid);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult block)) return List.of();
        BlockPos pos = block.getBlockPos();
        var rows = new ArrayList<String>();
        String location = xyz(pos.getX(), pos.getY(), pos.getZ());
        if (fluid) {
            var state = c.level.getFluidState(pos);
            rows.add(line(entry.translationKey(), BuiltInRegistries.FLUID.getKey(state.getType()) + " · " + location));
            properties(rows, state.getValues());
            tags(rows, state.getTags().map(tag -> "#" + tag.location()).toList());
        } else {
            var state = c.level.getBlockState(pos);
            rows.add(line(entry.translationKey(), BuiltInRegistries.BLOCK.getKey(state.getBlock()) + " · " + location));
            properties(rows, state.getValues());
            tags(rows, state.getTags().map(tag -> "#" + tag.location()).toList());
        }
        return rows;
    }

    private static List<String> targetEntity(Minecraft mc) {
        Entity entity = mc.crosshairPickEntity;
        if (entity == null) return List.of();
        var rows = new ArrayList<>(value(VanillaEntry.TARGET_ENTITY, BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())));
        tags(rows, entity.getType().builtInRegistryHolder().tags().map(tag -> "#" + tag.location()).toList());
        return rows;
    }

    private static void properties(List<String> rows, Map<Property<?>, Comparable<?>> values) {
        for (var entry : values.entrySet()) {
            var property = entry.getKey();
            Object constant = entry.getValue();
            String key = translatedName("f3ather.value.property." + property.getName(), property.getName());
            String value = constant instanceof Boolean flag ? tr(flag ? "true" : "false")
                    : Util.getPropertyName(property, entry.getValue());
            rows.add(tr("detail", key, value));
        }
    }

    private static void tags(List<String> rows, List<String> tags) {
        tags.forEach(tag -> rows.add(tr("tag", tag)));
    }

    private static long usedHeap() {
        Runtime vm = Runtime.getRuntime();
        return vm.totalMemory() - vm.freeMemory();
    }

    private static String xyz(Object x, Object y, Object z) { return "X " + x + " · Y " + y + " · Z " + z; }
    private static String status(boolean enabled) { return tr(enabled ? "on" : "off"); }
    private static String decimal(double value, int places) {
        return String.format(Locale.ROOT, switch (places) {
            case 0 -> "%.0f";
            case 1 -> "%.1f";
            case 2 -> "%.2f";
            case 3 -> "%.3f";
            default -> "%.5f";
        }, value);
    }
    private static String tr(String key, Object... values) {
        return Component.translatable("f3ather.value." + key, values).getString();
    }
    private static String translatedName(String key, String fallback) {
        return Component.translatableWithFallback(key, fallback).getString();
    }
    private static List<String> value(VanillaEntry entry, Object value) {
        return List.of(line(entry.translationKey(), value));
    }
    private static String line(String label, Object value) {
        return tr("detail", Component.translatable(label).getString(), String.valueOf(value));
    }

    private static final class Context {
        final Minecraft mc;
        final ClientLevel level;
        final Entity camera;
        final BlockPos pos;
        final ChunkPos chunk;
        final IntegratedServer integrated;
        final ServerLevel server;
        final int charts;
        private LevelChunk clientChunk;
        private boolean checked;

        Context(Minecraft mc, ClientLevel level, Entity camera, int charts) {
            this.mc = mc;
            this.level = level;
            this.camera = camera;
            this.charts = charts;
            pos = camera.blockPosition();
            chunk = new ChunkPos(pos);
            integrated = mc.getSingleplayerServer();
            server = integrated == null ? null : integrated.getLevel(level.dimension());
        }

        LevelChunk clientChunk() {
            if (!checked) {
                checked = true;
                clientChunk = level.getChunkSource().getChunk(chunk.x, chunk.z, ChunkStatus.FULL, false);
                if (clientChunk != null && clientChunk.isEmpty()) clientChunk = null;
            }
            return clientChunk;
        }
    }
}
