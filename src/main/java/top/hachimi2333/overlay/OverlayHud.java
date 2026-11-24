package top.hachimi2333.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import top.hachimi2333.overlay.OverlaySettings.BuiltIn;
import top.hachimi2333.overlay.OverlaySettings.Region;
import top.hachimi2333.overlay.OverlaySettings.Tab;
import top.hachimi2333.render.FastDebugText;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static top.hachimi2333.overlay.VanillaEntry.*;

public final class OverlayHud {
    private static final ResourceLocation LOGO = ResourceLocation.fromNamespaceAndPath("f3ather", "textures/gui/logo.png");
    private static final int TEXT = 0xffe0e0e0;
    private static final int BACKGROUND = 0x90505050;
    private static final int ACTIVE = 0xff9ce2d8;
    private static final int BAR_START = 20;
    private static final int CHART_SPACE = 72;

    private final AddonEntries addons;
    private final FastDebugText text = new FastDebugText();
    private final SampleClock clock = new SampleClock();
    private List<AddonEntries.Row> left = List.of();
    private List<AddonEntries.Row> right = List.of();
    private ClientPacketListener connection;
    private ClientLevel level;
    private Entity camera;
    private boolean reduced;
    private int chartFlags;
    private boolean modsDetected;
    private boolean samplingEditor;
    private String active;
    private int page;
    private int pageCount = 1;
    private Frame latest = Frame.EMPTY;

    public OverlayHud(AddonEntries addons) { this.addons = addons; }

    public FastDebugText text() { return text; }
    public String activeTab() { return active; }
    public boolean modsDetected() { return modsDetected; }
    public boolean samplingEditor() { return samplingEditor; }

    public void invalidate() {
        clock.invalidate();
        latest = Frame.EMPTY;
    }

    public void resetWorld() {
        invalidate();
        connection = null;
        level = null;
        camera = null;
        left = List.of();
        right = List.of();
        modsDetected = false;
        active = null;
        page = 0;
        pageCount = 1;
    }

    public void reloadResources() {
        text.clear();
        invalidate();
    }

    public boolean begin(Minecraft mc, OverlaySettings settings, int charts) {
        if (mc.getConnection() != connection) {
            resetWorld();
            connection = mc.getConnection();
        }
        boolean reducedNow = mc.showOnlyReducedInfo();
        if (level != mc.level || camera != mc.getCameraEntity() || reduced != reducedNow || chartFlags != charts) {
            invalidate();
        }
        level = mc.level;
        camera = mc.getCameraEntity();
        reduced = reducedNow;
        chartFlags = charts;
        return clock.due(System.nanoTime(), settings.refreshHz());
    }

    public AddonEntries.Column column(Minecraft mc, boolean isLeft, List<String> fresh) {
        if (fresh != null) {
            var rows = VanillaEntry.label(fresh, isLeft, mc.getSingleplayerServer() != null, mc.isDemo());
            if (isLeft) left = rows;
            else right = rows;
        }
        return addons.column(isLeft, isLeft ? left : right);
    }

    public void accept(AddonEntries.Column leftColumn, AddonEntries.Column rightColumn, OverlaySettings settings) {
        var grouped = new HashMap<String, List<String>>();
        var leftAddons = new ArrayList<String>();
        var rightAddons = new ArrayList<String>();
        boolean foundLeft = addons.collect(leftColumn, grouped, leftAddons);
        boolean foundRight = addons.collect(rightColumn, grouped, rightAddons);
        modsDetected |= foundLeft || foundRight;
        latest = new Frame(grouped, leftAddons, rightAddons);
        var tabs = settings.visibleTabs(modsDetected);
        if (active == null || !tabs.contains(active)) select(tabs.isEmpty() ? null : tabs.getFirst());
    }

    public void cycle(OverlaySettings settings) {
        select(OverlaySettings.cycle(settings.visibleTabs(modsDetected), active));
    }

    public boolean supportsPages() {
        return active != null && (active.equals(BuiltIn.MODS.id()) || !isBuiltIn(active));
    }

    public void advancePage(int direction) { page = Math.clamp(page + direction, 0, pageCount - 1); }

    private void select(String id) {
        active = id;
        page = 0;
        pageCount = 1;
    }

    private static boolean isBuiltIn(String id) {
        for (BuiltIn tab : BuiltIn.values()) if (tab.id().equals(id)) return true;
        return false;
    }

    public static Component title(OverlaySettings settings, String id) {
        Tab tab = settings.customTab(id);
        return tab == null ? Component.translatable("f3ather.tab." + id) : Component.literal(tab.name());
    }

    public void drawHeader(GuiGraphics graphics, OverlaySettings settings, String selected, int top, String indicator) {
        Font font = Minecraft.getInstance().font;
        var tabs = settings.visibleTabs(modsDetected);
        if (selected != null && !tabs.contains(selected)) tabs.add(selected);
        graphics.blit(LOGO, 2, top + 1, 0, 0, 16, 16, 16, 16);
        int limit = graphics.guiWidth() - 2;
        if (indicator != null) {
            int x = limit - text.width(font, indicator, TEXT);
            if (x > BAR_START) {
                graphics.fill(x - 1, top + 4, limit + 1, top + 13, BACKGROUND);
                text.draw(graphics, font, indicator, x, top + 5, TEXT);
                limit = x - 4;
            }
        }
        if (tabs.isEmpty() || limit <= BAR_START) return;
        var labels = new ArrayList<String>(tabs.size());
        var widths = new ArrayList<Integer>(tabs.size());
        int activeStart = 0;
        int activeEnd = 0;
        int total = 0;
        for (String id : tabs) {
            String label = title(settings, id).getString();
            boolean current = id.equals(selected);
            int width = text.width(font, label, current ? ACTIVE : TEXT) + 9;
            if (current) {
                activeStart = total;
                activeEnd = total + width;
            }
            total += width;
            labels.add(label);
            widths.add(width);
        }
        int available = limit - BAR_START;
        int overflow = Math.max(0, total - available);
        int offset = Math.clamp(Math.min(activeStart, activeEnd - available), 0, overflow);
        graphics.enableScissor(BAR_START, top, limit, top + HudLayout.HEADER);
        int x = BAR_START - offset;
        for (int i = 0; i < labels.size() && x < limit; i++) {
            int width = widths.get(i);
            if (x + width > BAR_START) {
                boolean current = tabs.get(i).equals(selected);
                graphics.fill(x, top + 4, x + width - 2, top + 14, BACKGROUND);
                if (current) graphics.fill(x, top + 13, x + width - 2, top + 14, ACTIVE);
                text.draw(graphics, font, labels.get(i), x + 3, top + 5, current ? ACTIVE : TEXT);
            }
            x += width;
        }
        graphics.disableScissor();
    }

    public void drawSelected(GuiGraphics graphics, OverlaySettings settings) {
        var rows = selectedRows(settings);
        int top = HudLayout.HEADER;
        var bottoms = bottoms(graphics.guiHeight(), top);
        boolean custom = active != null && !isBuiltIn(active);
        pageCount = supportsPages()
                ? HudLayout.pageCount(graphics.guiWidth(), top, bottoms.left(), bottoms.right(), rows, custom) : 1;
        page = Math.min(page, pageCount - 1);
        drawHeader(graphics, settings, active, 0, pageCount == 1 ? null
                : Component.translatable("f3ather.hud.page", page + 1, pageCount).getString());
        drawRows(graphics, rows, top, bottoms.left(), bottoms.right(), page, custom);
    }

    private Map<Region, List<String>> selectedRows(OverlaySettings settings) {
        if (active == null) return Map.of();
        if (active.equals(BuiltIn.SIMPLE.id())) {
            return Map.of(Region.TOP_LEFT, join(latest, FPS, XYZ, BLOCK_POSITION, CHUNK_RELATIVE, FACING, BIOME));
        }
        if (active.equals(BuiltIn.PERFORMANCE.id())) {
            return Map.of(Region.TOP_LEFT, join(latest, FPS, SERVER, RENDERED_CHUNKS, RENDERED_ENTITIES),
                    Region.TOP_RIGHT, join(latest, MEMORY, CPU, DISPLAY, GPU));
        }
        if (active.equals(BuiltIn.WORLD.id())) {
            return Map.of(Region.TOP_LEFT, join(latest, DIMENSION, XYZ, BLOCK_POSITION, CHUNK_POSITION,
                            CHUNK_RELATIVE, FACING, LIGHT_LEVEL, HEIGHTMAPS, BIOME, DIFFICULTY, SPAWNING),
                    Region.TOP_RIGHT, join(latest, TARGET_BLOCK, TARGET_FLUID, TARGET_ENTITY));
        }
        if (active.equals(BuiltIn.MODS.id())) {
            return Map.of(Region.TOP_LEFT, latest.leftAddons(), Region.TOP_RIGHT, latest.rightAddons());
        }
        Tab tab = settings.customTab(active);
        return tab == null ? Map.of() : customRows(tab, latest);
    }

    public void updateEditorPreview(Minecraft mc, GuiGraphics graphics) {
        if (mc.level == null || mc.getCameraEntity() == null || mc.getConnection() == null
                || mc.getDebugOverlay().showDebugScreen()) return;
        samplingEditor = true;
        try {
            mc.getDebugOverlay().render(graphics);
        } finally {
            samplingEditor = false;
        }
    }

    public List<String> previewLines(String entry, boolean inWorld) {
        if (inWorld) return latest.groups().getOrDefault(entry, List.of());
        return PreviewSamples.lines(entry);
    }

    public Bottoms bottoms(int height, int top) {
        int bottom = height - ((chartFlags & 6) == 0 ? 0 : CHART_SPACE);
        int right = bottom;
        if ((chartFlags & 1) != 0) {
            right = Math.min(bottom, height - (int) Math.ceil(416 / Minecraft.getInstance().getWindow().getGuiScale()));
        }
        return new Bottoms(Math.max(top, bottom), Math.max(top, right));
    }

    public record Bottoms(int left, int right) {}

    public void drawPreview(GuiGraphics graphics, OverlaySettings settings, Tab tab, int headerTop,
                            Bottoms bottom, Map<Region, List<String>> rows, int page, int pages) {
        drawHeader(graphics, settings, tab.id(), headerTop, pages == 1 ? null
                : Component.translatable("f3ather.hud.page", page + 1, pages).getString());
        drawRows(graphics, rows, headerTop + HudLayout.HEADER, bottom.left(), bottom.right(), page, true);
    }

    private void drawRows(GuiGraphics graphics, Map<Region, List<String>> rows, int top, int leftBottom,
                          int rightBottom, int page, boolean fourCorners) {
        Font font = Minecraft.getInstance().font;
        for (Region region : Region.values()) {
            var bounds = HudLayout.bounds(graphics.guiWidth(), top, region.left() ? leftBottom : rightBottom, region, rows, fourCorners);
            var lines = HudLayout.page(rows.getOrDefault(region, List.of()), bounds, page);
            if (lines.isEmpty()) continue;
            int start = bounds.firstY(region.top(), lines.size());
            graphics.enableScissor(fourCorners ? bounds.left() : 0, bounds.top(),
                    fourCorners ? bounds.right() : graphics.guiWidth(), bounds.bottom());
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (line.isEmpty()) continue;
                int width = text.width(font, line, TEXT);
                int x = region.left() ? bounds.left() + 2 : bounds.right() - 2 - width;
                int y = start + index * HudLayout.ROW_HEIGHT;
                graphics.fill(x - 1, y - 1, x + width + 1, y + HudLayout.ROW_HEIGHT - 1, BACKGROUND);
            }
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (line.isEmpty()) continue;
                int width = text.width(font, line, TEXT);
                int x = region.left() ? bounds.left() + 2 : bounds.right() - 2 - width;
                text.draw(graphics, font, line, x, start + index * HudLayout.ROW_HEIGHT, TEXT);
            }
            graphics.disableScissor();
        }
    }

    private static Map<Region, List<String>> customRows(Tab tab, Frame frame) {
        var rows = new EnumMap<Region, List<String>>(Region.class);
        for (Region area : Region.values()) {
            rows.put(area, tab.entries(area).stream()
                    .flatMap(id -> frame.groups().getOrDefault(id, List.of()).stream()).toList());
        }
        return rows;
    }

    private static List<String> join(Frame frame, VanillaEntry... entries) {
        var result = new ArrayList<String>();
        for (VanillaEntry entry : entries) result.addAll(frame.groups().getOrDefault(entry.id(), List.of()));
        return result;
    }

    private record Frame(Map<String, List<String>> groups, List<String> leftAddons, List<String> rightAddons) {
        private static final Frame EMPTY = new Frame(Map.of(), List.of(), List.of());
    }

    static final class SampleClock {
        private int hz = -1;
        private long next;

        boolean due(long now, int frequency) {
            if (hz == frequency && frequency != 0 && now - next < 0) return false;
            if (hz != frequency || frequency == 0) next = now;
            hz = frequency;
            if (hz != 0) {
                long interval = 1_000_000_000L / hz;
                next += ((now - next) / interval + 1) * interval;
            }
            return true;
        }

        void invalidate() { hz = -1; }
    }
}
