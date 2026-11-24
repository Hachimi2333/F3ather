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
import java.util.Objects;

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
    private final OverlayMotion motion = new OverlayMotion();
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
    private PresentedFrame presented;

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
        resetMotion();
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

    public AddonEntries.Column column(boolean isLeft, List<AddonEntries.Row> fresh) {
        if (fresh != null) {
            if (isLeft) left = fresh;
            else right = fresh;
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
        String next = OverlaySettings.cycle(settings.visibleTabs(modsDetected), active);
        if (!Objects.equals(active, next)) select(next);
        motion.reveal(System.nanoTime(), settings.disableAnimations());
    }

    public boolean supportsPages() {
        return active != null && (active.equals(BuiltIn.MODS.id()) || !isBuiltIn(active));
    }

    public void advancePage(int direction, OverlaySettings settings) {
        int next = Math.clamp(page + direction, 0, pageCount - 1);
        if (page != next) {
            page = next;
            motion.reveal(System.nanoTime(), settings.disableAnimations());
        }
    }

    public void resetMotion() {
        motion.reset();
        presented = null;
    }

    public void toggle(boolean visible, OverlaySettings settings) {
        invalidate();
        if (settings.compatibility()) {
            resetMotion();
        } else if (visible) {
            motion.open(System.nanoTime(), settings.disableAnimations());
        } else if (presented != null) {
            motion.close(System.nanoTime(), settings.disableAnimations());
        } else {
            resetMotion();
        }
    }

    public HudLayout.Position position(OverlaySettings settings, int width) {
        return motion.position(System.nanoTime(), settings.disableAnimations(), width);
    }

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
        graphics.enableScissor(BAR_START, Math.max(0, top), limit, top + HudLayout.HEADER);
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

    public void drawSelected(GuiGraphics graphics, OverlaySettings settings, HudLayout.Position position) {
        var rows = selectedRows(settings);
        int top = position.layoutHeader();
        var bottoms = bottoms(graphics.guiHeight(), top);
        boolean custom = active != null && !isBuiltIn(active);
        pageCount = supportsPages()
                ? HudLayout.pageCount(graphics.guiWidth(), top, bottoms.left(), bottoms.right(), rows, custom) : 1;
        page = Math.min(page, pageCount - 1);
        presented = new PresentedFrame(settings, active, rows, page, pageCount, custom);
        drawPresentedHeader(graphics, presented, position);
        drawRows(graphics, rows, position, bottoms, page, custom);
    }

    public void presentVanilla(GuiGraphics graphics, OverlaySettings settings, HudLayout.Position position,
                               List<String> leftLines, List<String> rightLines) {
        var rows = Map.<Region, List<String>>of(Region.TOP_LEFT, new ArrayList<>(leftLines),
                Region.TOP_RIGHT, new ArrayList<>(rightLines));
        presented = new PresentedFrame(settings, BuiltIn.VANILLA.id(), rows, 0, 1, false);
        drawPresentedHeader(graphics, presented, position);
    }

    public void drawExit(Minecraft mc, GuiGraphics graphics, OverlaySettings settings) {
        if (presented == null || mc.getDebugOverlay().showDebugScreen()) return;
        if (settings.compatibility() || mc.options.hideGui || mc.level == null || mc.level != level
                || mc.getConnection() != connection || mc.getCameraEntity() != camera
                || mc.showOnlyReducedInfo() != reduced) {
            resetMotion();
            return;
        }
        int width = graphics.guiWidth();
        var position = motion.exitPosition(System.nanoTime(), settings.disableAnimations(), width);
        if (position == null) {
            presented = null;
            return;
        }
        drawPresentedHeader(graphics, presented, position);
        var rows = presented.rows();
        if (BuiltIn.VANILLA.id().equals(presented.tab())) {
            for (Region region : List.of(Region.TOP_LEFT, Region.TOP_RIGHT)) {
                boolean isLeft = region.left();
                int offset = position.columnX(isLeft);
                if (Math.abs(offset) >= width) continue;
                graphics.enableScissor(Math.max(0, offset), 0, Math.min(width, width + offset), graphics.guiHeight());
                drawTextRows(graphics, rows.get(region), isLeft, offset, width + offset, position.headerHeight() + 2);
                graphics.disableScissor();
            }
        } else {
            drawRows(graphics, rows, position, bottoms(graphics.guiHeight(), position.layoutHeader()),
                    presented.page(), presented.custom());
        }
    }

    private void drawPresentedHeader(GuiGraphics graphics, PresentedFrame frame, HudLayout.Position position) {
        if (position.headerHeight() != 0) {
            drawHeader(graphics, frame.settings(), frame.tab(), position.headerY(), frame.pages() == 1 ? null
                    : Component.translatable("f3ather.hud.page", frame.page() + 1, frame.pages()).getString());
        }
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

    public void drawPreview(GuiGraphics graphics, OverlaySettings settings, Tab tab,
                            Bottoms bottom, Map<Region, List<String>> rows, int page, int pages) {
        drawHeader(graphics, settings, tab.id(), 0, pages == 1 ? null
                : Component.translatable("f3ather.hud.page", page + 1, pages).getString());
        drawRows(graphics, rows, HudLayout.Position.EXPANDED, bottom, page, true);
    }

    private void drawRows(GuiGraphics graphics, Map<Region, List<String>> rows, HudLayout.Position position,
                          Bottoms layoutBottoms, int page, boolean fourCorners) {
        int width = graphics.guiWidth();
        int visibleHeader = position.headerHeight();
        int layoutHeader = position.layoutHeader();
        var visibleBottoms = visibleHeader == layoutHeader ? layoutBottoms : bottoms(graphics.guiHeight(), visibleHeader);
        for (Region region : Region.values()) {
            boolean left = region.left();
            int layoutBottom = left ? layoutBottoms.left() : layoutBottoms.right();
            var bounds = HudLayout.bounds(width, layoutHeader, layoutBottom, region, rows, fourCorners);
            var lines = HudLayout.page(rows.getOrDefault(region, List.of()), bounds, page);
            if (lines.isEmpty()) continue;
            int offsetX = position.columnX(left);
            int offsetY = region.top() ? visibleHeader - layoutHeader : 0;
            int clipLeft = Math.max(0, (fourCorners ? bounds.left() : 0) + offsetX);
            int clipRight = Math.min(width, (fourCorners ? bounds.right() : width) + offsetX);
            var visible = visibleHeader == layoutHeader ? bounds : HudLayout.bounds(width, visibleHeader,
                    left ? visibleBottoms.left() : visibleBottoms.right(), region, rows, fourCorners);
            if (clipRight <= clipLeft || visible.bottom() <= visible.top()) continue;
            int start = bounds.firstY(region.top(), lines.size()) + offsetY;
            graphics.enableScissor(clipLeft, visible.top(), clipRight, visible.bottom());
            drawTextRows(graphics, lines, left, bounds.left() + offsetX, bounds.right() + offsetX, start);
            graphics.disableScissor();
        }
    }

    private void drawTextRows(GuiGraphics graphics, List<String> lines, boolean left, int leftEdge, int rightEdge, int start) {
        Font font = Minecraft.getInstance().font;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line == null || line.isEmpty()) continue;
            int lineWidth = text.width(font, line, TEXT);
            int x = left ? leftEdge + 2 : rightEdge - 2 - lineWidth;
            int y = start + index * HudLayout.ROW_HEIGHT;
            graphics.fill(x - 1, y - 1, x + lineWidth + 1, y + HudLayout.ROW_HEIGHT - 1, BACKGROUND);
        }
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line == null || line.isEmpty()) continue;
            int x = left ? leftEdge + 2 : rightEdge - 2 - text.width(font, line, TEXT);
            text.draw(graphics, font, line, x, start + index * HudLayout.ROW_HEIGHT, TEXT);
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

    private record PresentedFrame(OverlaySettings settings, String tab, Map<Region, List<String>> rows,
                                  int page, int pages, boolean custom) {}

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
