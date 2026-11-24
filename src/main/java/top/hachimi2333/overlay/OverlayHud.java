package top.hachimi2333.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import top.hachimi2333.render.ModernFont;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class OverlayHud {
    private static final float TEXT_SIZE = 10.5f;
    private static final int ROW_HEIGHT = 15;
    private static final int GUTTER = 7;
    private static final int INSET = 8;
    private static final int MAX_ROW_TEXT = 2048;
    private static final int MAX_FRAME_ROWS = 2048;
    private static final long DISCOVERY_INTERVAL = 500_000_000L;

    private final DebugData data = new DebugData();
    private final AddonEntries addons;
    private List<String> lastRows = List.of();
    private List<String> wrappedRows = List.of();
    private List<Integer> rowWidths = List.of();
    private int lastWidth;
    private int page;
    private int pageCount = 1;
    private long nextDiscovery;
    private boolean addonsVisible;
    private OverlaySettings lastSettings;

    public OverlayHud(AddonEntries addons) {
        this.addons = addons;
    }

    public void invalidate() {
        data.invalidate();
        lastRows = List.of();
        wrappedRows = List.of();
        rowWidths = List.of();
        page = 0;
        pageCount = 1;
        nextDiscovery = 0;
    }

    public void advancePage(int direction) {
        page = Math.clamp(page + direction, 0, pageCount - 1);
    }

    public Frame begin(Minecraft mc, OverlaySettings settings, int charts) {
        if (lastSettings != settings) {
            lastSettings = settings;
            addonsVisible = settings.enabled().stream().anyMatch(id -> id.startsWith("addon:"))
                    || settings.legacyAddons().containsValue(true);
            nextDiscovery = 0;
        }
        long now = System.nanoTime();
        boolean trackSources = addonsVisible || nextDiscovery == 0 || now >= nextDiscovery;
        if (trackSources) nextDiscovery = now + DISCOVERY_INTERVAL;
        var values = data.sample(mc, settings, charts);
        var left = new ArrayList<AddonEntries.Row>();
        var right = new ArrayList<AddonEntries.Row>();
        for (VanillaEntry entry : VanillaEntry.ALL) {
            List<String> lines = values.get(entry);
            if (lines == null) continue;
            var column = entry.rightColumn() ? right : left;
            for (String line : lines) column.add(new AddonEntries.Row(entry.id(), line));
        }
        return new Frame(addons.column(true, left, trackSources), addons.column(false, right, trackSources), settings, charts);
    }

    public void draw(GuiGraphics graphics, Frame frame) {
        Minecraft.getInstance().getProfiler().push("f3ather_hud");
        try {
            drawFrame(graphics, frame);
        } finally {
            Minecraft.getInstance().getProfiler().pop();
        }
    }

    private void drawFrame(GuiGraphics graphics, Frame frame) {
        var grouped = new LinkedHashMap<String, List<String>>();
        addons.collect(frame.left(), grouped, frame.settings());
        addons.collect(frame.right(), grouped, frame.settings());
        if (grouped.isEmpty()) {
            page = 0;
            pageCount = 1;
            return;
        }
        var rows = new ArrayList<String>();
        entries:
        for (String id : frame.settings().ordered(new ArrayList<>(grouped.keySet()))) {
            for (String line : grouped.get(id)) {
                if (rows.size() == MAX_FRAME_ROWS) break entries;
                rows.add(limitLength(line));
            }
        }
        int columns = graphics.guiWidth() >= 560 ? 2 : 1;
        int width = Math.min(420, (graphics.guiWidth() - GUTTER * (columns + 1)) / columns);
        int chartSpace = (frame.charts() & 1) != 0 ? 150 : (frame.charts() & 6) != 0 ? 75 : 0;
        int visible = Math.max(1, (graphics.guiHeight() - GUTTER - 3 * INSET - chartSpace) / ROW_HEIGHT);
        if (width < 2 * INSET + 10 || graphics.guiHeight() < ROW_HEIGHT + 2 * INSET) return;
        ModernFont font = ModernFont.INSTANCE;
        if (lastWidth != width || !rows.equals(lastRows)) {
            lastWidth = width;
            lastRows = rows;
            var wrapped = new ArrayList<String>();
            for (String line : rows) wrap(font, line, width - 2 * INSET, wrapped);
            wrappedRows = wrapped;
            rowWidths = wrapped.stream().map(line -> (int) Math.ceil(font.width(line, TEXT_SIZE))).toList();
        }
        int perPage = columns * visible;
        pageCount = Math.max(1, (wrappedRows.size() + perPage - 1) / perPage);
        if (pageCount > 1 && visible > 1) {
            visible--;
            perPage = columns * visible;
            pageCount = (wrappedRows.size() + perPage - 1) / perPage;
        }
        page = Math.min(page, pageCount - 1);
        int start = page * perPage;
        int end = Math.min(start + perPage, wrappedRows.size());
        for (int i = start; i < end; i++) font.prepareText(wrappedRows.get(i));
        String pageLabel = pageCount > 1 ? Component.translatable("f3ather.hud.page", page + 1, pageCount).getString() : "";
        int pageLabelWidth = (int) Math.ceil(font.width(pageLabel, TEXT_SIZE));
        if (!pageLabel.isEmpty()) font.prepareText(pageLabel);
        font.upload();

        for (int column = 0; column < columns; column++) {
            int first = start + column * visible;
            int count = Math.min(visible, end - first);
            if (count <= 0) break;
            int x = GUTTER + column * (width + GUTTER);
            int contentWidth = column == 0 ? pageLabelWidth : 0;
            for (int i = first; i < first + count; i++) contentWidth = Math.max(contentWidth, rowWidths.get(i));
            int panelWidth = Math.min(width, contentWidth + 2 * INSET);
            int height = 2 * INSET + count * ROW_HEIGHT + (column == 0 && !pageLabel.isEmpty() ? ROW_HEIGHT : 0);
            graphics.fill(x, GUTTER, x + panelWidth, GUTTER + height, 0xdd111923);
            graphics.fill(x, GUTTER, x + 2, GUTTER + height, 0xff54d6ac);
            graphics.flush();
            for (int row = 0; row < count; row++) {
                font.drawPrepared(graphics, wrappedRows.get(first + row), x + INSET,
                        GUTTER + INSET + row * ROW_HEIGHT, TEXT_SIZE, 0xffebf2f8);
            }
            if (column == 0 && !pageLabel.isEmpty()) {
                font.drawPrepared(graphics, pageLabel, x + INSET, GUTTER + INSET + count * ROW_HEIGHT,
                        TEXT_SIZE, 0xff80dcbf);
            }
            graphics.flush();
        }
    }

    private static void wrap(ModernFont font, String row, int width, List<String> lines) {
        String remaining = row;
        while (font.width(remaining, TEXT_SIZE) > width) {
            String fitted = font.fit(remaining, TEXT_SIZE, width);
            int length = fitted.length() - 1;
            if (length <= 0) break;
            int whitespace = remaining.lastIndexOf(' ', length);
            if (whitespace > length / 2) length = whitespace;
            lines.add(remaining.substring(0, length).stripTrailing());
            remaining = remaining.substring(length).stripLeading();
        }
        if (!remaining.isBlank()) lines.add(remaining);
    }

    private static String limitLength(String line) {
        if (line.length() <= MAX_ROW_TEXT) return line;
        int end = Character.isHighSurrogate(line.charAt(MAX_ROW_TEXT - 1)) ? MAX_ROW_TEXT - 1 : MAX_ROW_TEXT;
        return line.substring(0, end) + "…";
    }

    public record Frame(AddonEntries.Column left, AddonEntries.Column right, OverlaySettings settings, int charts) {}
}
