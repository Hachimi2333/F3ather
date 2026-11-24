package top.hachimi2333.overlay;

import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

final class PreviewSamples {
    private static final Map<String, List<String>> LINES = Map.ofEntries(
            entry("version", List.of("Minecraft 1.21.1 (1.21.1/neoforge)")),
            entry("fps", List.of("60 fps T: 60 vsync fancy-clouds B: 2")),
            entry("server", List.of("Integrated server @ 3.2/50.0 ms, 24 tx, 25 rx")),
            entry("rendered_chunks", List.of("C: 237/289 D: 8, pC: 000, pU: 0, aB: 12")),
            entry("rendered_entities", List.of("E: 16/48, B: 0, SD: 8")),
            entry("particles", List.of("P: 2. T: 48")),
            entry("client_chunks", List.of("Client Chunk Cache: 529, 289")),
            entry("server_chunks", List.of("ServerChunkCache: 441")),
            entry("dimension", List.of("minecraft:overworld FC: 0")),
            entry("xyz", List.of("XYZ: 128.500 / 64.00000 / -42.500")),
            entry("block_position", List.of("Block: 128 64 -43 [0 0 5]")),
            entry("chunk_position", List.of("Chunk: 8 4 -3 [8 29 in r.0.-1.mca]")),
            entry("chunk_relative", List.of("Chunk-relative: 0 0 5")),
            entry("facing", List.of("Facing: north (Towards negative Z) (180.0 / 0.0)")),
            entry("light_level", List.of("Client Light: 15 (15 sky, 0 block)")),
            entry("heightmaps", List.of("CH S: 65 M: 65", "SH S: 65 O: 64 M: 65 ML: 65")),
            entry("biome", List.of("Biome: minecraft:plains")),
            entry("difficulty", List.of("Local Difficulty: 1.50 // 0.00 (Day 0)")),
            entry("blending", List.of("Blending: Old")),
            entry("world_generation", List.of("NoiseRouter T: 0.192 V: 0.066 C: 0.177 E: -0.041 D: 0.061 W: 0.057")),
            entry("spawning", List.of("SC: 289, M: 42, C: 6, A: 0, U: 0, W: 0, W: 0, M: 0")),
            entry("post_effect", List.of("Shader: minecraft:shaders/post/creeper.json")),
            entry("sound", List.of("Sounds: 2/247 + 0/8 (Mood 0%)")),
            entry("debug_chart_hint", List.of("Debug charts: [F3+1] Profiler hidden; [F3+2] FPS + TPS hidden; [F3+3] Ping hidden")),
            entry("debug_help", List.of("For help: press F3 + Q")),
            entry("demo", List.of("Demo time remaining: 19:59")),
            entry("java", List.of("Java: 21.0.13")),
            entry("memory", List.of("Mem: 25% 512/2048MB")),
            entry("allocation_rate", List.of("Allocation rate: 032MB/s")),
            entry("allocated", List.of("Allocated: 50% 1024MB")),
            entry("cpu", List.of("CPU: 8x Example CPU")),
            entry("display", List.of("Display: 1920x1080 (Example)")),
            entry("gpu", List.of("Example Graphics")),
            entry("opengl", List.of("4.6.0")),
            entry("target_block", List.of("§nTargeted Block: 128, 63, -43", "minecraft:grass_block", "snowy: §cfalse", "#minecraft:dirt")),
            entry("target_fluid", List.of("§nTargeted Fluid: 128, 62, -43", "minecraft:water", "#minecraft:water")),
            entry("target_entity", List.of("§nTargeted Entity", "minecraft:cow"))
    );

    private PreviewSamples() {}

    static List<String> lines(String id) {
        return id.startsWith("vanilla:") ? LINES.getOrDefault(id.substring(8), List.of()) : List.of();
    }
}
