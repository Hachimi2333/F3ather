package top.hachimi2333.overlay;

import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public enum VanillaEntry {
    VERSION, FPS, SERVER, RENDERED_CHUNKS, RENDERED_ENTITIES, PARTICLES, CLIENT_CHUNKS, SERVER_CHUNKS,
    DIMENSION, XYZ, BLOCK_POSITION, CHUNK_POSITION, CHUNK_RELATIVE, FACING, LIGHT_LEVEL, HEIGHTMAPS,
    BIOME, DIFFICULTY, BLENDING, WORLD_GENERATION, SPAWNING, POST_EFFECT, SOUND, DEBUG_CHART_HINT, DEBUG_HELP,
    DEMO, JAVA, MEMORY, ALLOCATION_RATE, ALLOCATED, CPU, DISPLAY, GPU, OPENGL,
    TARGET_BLOCK, TARGET_FLUID, TARGET_ENTITY;

    private static final VanillaEntry[] LEFT_HEADER = {
            VERSION, FPS, SERVER, RENDERED_CHUNKS, RENDERED_ENTITIES, PARTICLES, CLIENT_CHUNKS
    };
    private static final VanillaEntry[] RIGHT_DISPLAY = { DISPLAY, GPU, OPENGL };

    private final String name = name().toLowerCase(Locale.ROOT);
    private final String id = "vanilla:" + name;

    public String id() { return id; }
    public String translationKey() { return "f3ather.entry." + name; }

    static List<AddonEntries.Row> label(List<String> lines, boolean left, boolean integrated, boolean demo) {
        var result = new ArrayList<AddonEntries.Row>(lines.size());
        VanillaEntry target = null;
        int display = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            VanillaEntry entry = null;
            if (line != null && !line.isEmpty()) {
                if (left) {
                    entry = i < LEFT_HEADER.length ? LEFT_HEADER[i] : leftEntry(line, i, lines.size(), integrated);
                } else {
                    if (line.startsWith("Display: ")) display = i;
                    VanillaEntry header = rightEntry(line);
                    if (i == 0 && demo) entry = DEMO;
                    else if (header != null) {
                        entry = header;
                        if (i > display + 2) target = header;
                    } else if (display >= 0 && i - display < RIGHT_DISPLAY.length) entry = RIGHT_DISPLAY[i - display];
                    else entry = target;
                }
            } else {
                target = null;
            }
            result.add(new AddonEntries.Row(entry == null ? null : entry.id(), line));
        }
        return List.copyOf(result);
    }

    private static VanillaEntry leftEntry(String line, int index, int size, boolean integrated) {
        if (line.startsWith("Debug charts: ")) return DEBUG_CHART_HINT;
        if (line.startsWith("For help: ")) return DEBUG_HELP;
        if (line.startsWith("Chunk-relative: ")) return CHUNK_RELATIVE;
        if (line.indexOf(" FC: ") > 0) return DIMENSION;
        if (integrated && index == LEFT_HEADER.length) return SERVER_CHUNKS;
        if (line.startsWith("XYZ: ")) return XYZ;
        if (line.startsWith("Block: ")) return BLOCK_POSITION;
        if (line.startsWith("Chunk: ")) return CHUNK_POSITION;
        if (line.startsWith("Facing: ")) return FACING;
        if (line.startsWith("Client Light: ") || line.equals("Waiting for chunk...")) return LIGHT_LEVEL;
        if (line.startsWith("CH ") || line.startsWith("SH ")) return HEIGHTMAPS;
        if (line.startsWith("Biome: ")) return BIOME;
        if (line.startsWith("Local Difficulty: ")) return DIFFICULTY;
        if (line.startsWith("Blending: ")) return BLENDING;
        if (line.startsWith("SC: ")) return SPAWNING;
        if (line.startsWith("Shader: ")) return POST_EFFECT;
        if (index == size - 4 && line.contains(" (Mood ")) return SOUND;
        return integrated ? WORLD_GENERATION : null;
    }

    private static VanillaEntry rightEntry(String line) {
        if (line.startsWith("Java: ")) return JAVA;
        if (line.startsWith("Mem: ")) return MEMORY;
        if (line.startsWith("Allocation rate: ")) return ALLOCATION_RATE;
        if (line.startsWith("Allocated: ")) return ALLOCATED;
        if (line.startsWith("CPU: ")) return CPU;
        if (line.startsWith("Display: ")) return DISPLAY;
        String plain = ChatFormatting.stripFormatting(line);
        if (plain.startsWith("Targeted Block: ")) return TARGET_BLOCK;
        if (plain.startsWith("Targeted Fluid: ")) return TARGET_FLUID;
        if (plain.equals("Targeted Entity")) return TARGET_ENTITY;
        return null;
    }
}
