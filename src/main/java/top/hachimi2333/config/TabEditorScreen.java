package top.hachimi2333.config;

import com.nstut.openui.api.UIComponent;
import com.nstut.openui.api.Ui;
import com.nstut.openui.api.UiRender;
import com.nstut.openui.input.EventType;
import com.nstut.openui.input.PointerEvent;
import com.nstut.openui.layout.Alignment;
import com.nstut.openui.minecraft.UiScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.hachimi2333.F3ather;
import top.hachimi2333.overlay.HudLayout;
import top.hachimi2333.overlay.OverlaySettings.Region;
import top.hachimi2333.overlay.OverlaySettings.Tab;
import top.hachimi2333.overlay.VanillaEntry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.UnaryOperator;

import static top.hachimi2333.config.F3atherScreen.label;
import static top.hachimi2333.config.SettingsUi.*;

public final class TabEditorScreen extends UiScreen {
    private static final int ROW = HudLayout.ROW_HEIGHT;
    private final Screen parent;
    private final String id;
    private PreviewCanvas canvas;
    private UIComponent tools;
    private EntrySearch search;
    private boolean suppressSlash;

    public TabEditorScreen(Screen parent, String id) {
        super(label("edit"));
        this.parent = parent;
        this.id = id;
    }

    @Override
    protected UIComponent buildUI() {
        uiRuntime().theme(THEME);
        Tab tab = currentTab();
        var back = label("back");
        var add = label("add_entry");
        int buttonWidth = Math.max(font.width(back), font.width(add)) + 16;
        var toolbar = Ui.row(button(back, this::onClose).width(buttonWidth),
                Ui.text(Component.literal(tab == null ? "" : tab.name()))
                        .color(TEXT).centered().ellipsis().flex(),
                button(add, this::openSearch).filled().width(buttonWidth))
                .gap(5).align(Alignment.CENTER).fillWidth().height(21);
        tools = panel(toolbar, 4).width(Math.min(230, width - 28));
        canvas = new PreviewCanvas();
        return Ui.stack(canvas.fillWidth().fillHeight(), tools).align(Alignment.CENTER, Alignment.CENTER)
                .fillWidth().fillHeight();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int x, int y, float delta) {
        if (minecraft.level == null) renderPanorama(graphics, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, float delta) {
        if (currentTab() == null) {
            onClose();
            return;
        }
        F3ather.HUD.updateEditorPreview(minecraft, graphics);
        super.render(graphics, x, y, delta);
    }

    private void openSearch() {
        var tab = currentTab();
        if (tab == null || search != null && search.open()) return;
        search = new EntrySearch(uiRuntime(), font, width, height, tab, entry -> update(current ->
                current.move(entry, Region.TOP_LEFT, current.entries(Region.TOP_LEFT).size())));
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (search != null && search.open() && search.keyPressed(key)) return true;
        if (!uiRuntime().overlays().hasBlockingOverlay()) {
            if (key == GLFW.GLFW_KEY_SLASH) {
                suppressSlash = true;
                openSearch();
                return true;
            }
            if (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN) {
                canvas.turnPage(key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1);
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (suppressSlash) {
            suppressSlash = false;
            if (character == '/') return true;
        }
        return super.charTyped(character, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_SLASH) suppressSlash = false;
        return super.keyReleased(key, scan, modifiers);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    private Tab currentTab() { return ClientConfig.current().customTab(id); }

    private void update(UnaryOperator<Tab> edit) {
        var settings = ClientConfig.current();
        Tab tab = settings.customTab(id);
        if (tab != null) ClientConfig.save(settings.withTab(edit.apply(tab)));
    }

    private static String entryTitle(String id) {
        for (VanillaEntry entry : VanillaEntry.values()) {
            if (id.equals(entry.id())) return Component.translatable(entry.translationKey()).getString();
        }
        for (var entry : F3ather.ADDONS.entries()) {
            if (id.equals(entry.id())) return entry.title();
        }
        if (id.startsWith("addon:")) {
            int owner = id.indexOf(':', 6);
            int name = id.indexOf(':', owner + 1);
            int end = id.lastIndexOf(':');
            if (name >= 0 && end > name + 1) return id.substring(name + 1, end);
        }
        return id;
    }

    private final class PreviewCanvas extends UIComponent {
        private final EnumMap<Region, HudLayout.Rect> areas = new EnumMap<>(Region.class);
        private List<Placement> placements = List.of();
        private Placement pressed;
        private boolean dragging;
        private double pressX;
        private double pressY;
        private int page;
        private int pages = 1;

        PreviewCanvas() {
            on(EventType.MOUSE_DOWN, event -> {
                var pointer = (PointerEvent) event;
                if (pointer.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && at(pointer.x(), pointer.y()) != null) {
                    event.capturePointer();
                }
            });
        }

        @Override public int preferredWidth(Font font) { return 1; }
        @Override public int preferredHeight(Font font) { return 1; }

        @Override
        @SuppressWarnings("deprecation") // Minecraft 1.21.1 uses the same managed batch for debug text.
        public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY, float delta) {
            Tab tab = currentTab();
            if (tab == null) return;
            var rows = new EnumMap<Region, List<String>>(Region.class);
            var groups = new EnumMap<Region, List<Group>>(Region.class);
            boolean inWorld = minecraft.level != null;
            for (Region region : Region.values()) {
                var lines = new ArrayList<String>();
                var entries = new ArrayList<Group>();
                for (String entry : tab.entries(region)) {
                    int start = lines.size();
                    var values = F3ather.HUD.previewLines(entry, inWorld);
                    if (values.isEmpty()) lines.add("§7" + entryTitle(entry));
                    else lines.addAll(values);
                    entries.add(new Group(entry, start, lines.size()));
                }
                rows.put(region, lines);
                groups.put(region, entries);
            }
            int top = y + HudLayout.HEADER;
            var bottom = F3ather.HUD.bottoms(y + height, top);
            pages = HudLayout.pageCount(width, top, bottom.left(), bottom.right(), rows, true);
            page = Math.clamp(page, 0, pages - 1);
            var visible = new ArrayList<Placement>();
            for (Region region : Region.values()) {
                var area = HudLayout.region(width, top, region.left() ? bottom.left() : bottom.right(), region, true);
                areas.put(region, area);
                var lines = HudLayout.page(rows.get(region), area, page);
                if (lines.isEmpty()) continue;
                int start = page * area.capacity();
                int end = start + lines.size();
                int y0 = area.firstY(region.top(), lines.size());
                var entries = groups.get(region);
                for (int i = 0; i < entries.size(); i++) {
                    Group group = entries.get(i);
                    int first = Math.max(start, group.start());
                    int last = Math.min(end, group.end());
                    if (first >= last) continue;
                    int size = 0;
                    for (int line = first; line < last; line++) {
                        size = Math.max(size, font.width(rows.get(region).get(line)));
                    }
                    int length = Math.min(size + 16, area.right() - area.left());
                    int x0 = region.left() ? area.left() : area.right() - length;
                    visible.add(new Placement(group.entry(), region, i, x0, x0 + length,
                            y0 + (first - start) * ROW - 1, y0 + (last - start) * ROW - 1));
                }
            }
            placements = visible;
            graphics.drawManaged(() -> {
                F3ather.HUD.drawPreview(graphics, ClientConfig.current(), tab, bottom, rows, page, pages);
                if (!uiRuntime().overlays().hasBlockingOverlay()) drawControls(graphics, mouseX, mouseY, tab);
            });
        }

        private void drawControls(GuiGraphics graphics, int mouseX, int mouseY, Tab tab) {
            boolean overTools = mouseX >= tools.getX() && mouseX < tools.getX() + tools.getWidth()
                    && mouseY >= tools.getY() && mouseY < tools.getY() + tools.getHeight();
            var hovered = overTools ? null : at(mouseX, mouseY);
            if (!dragging && hovered != null) {
                graphics.renderOutline(hovered.left(), hovered.top(), hovered.right() - hovered.left(),
                        hovered.bottom() - hovered.top(), 0xcc81c0b5);
                int actionX = hovered.region().left() ? hovered.right() - 10 : hovered.left() + 2;
                graphics.drawString(font, "×", actionX, hovered.top() + 1, ACCENT, false);
            }
            if (!dragging || pressed == null) return;
            var target = dropAt(mouseX, mouseY, tab);
            if (target != null) {
                var area = areas.get(target.region());
                graphics.renderOutline(area.left() + 1, area.top(), area.right() - area.left() - 2,
                        area.bottom() - area.top(), 0x8860b2a4);
                graphics.fill(area.left() + 2, target.y(), area.right() - 2, target.y() + 1, ACCENT);
            }
            String title = font.plainSubstrByWidth(entryTitle(pressed.entry()), Math.max(16, width / 2 - 14));
            int boxWidth = font.width(title) + 12;
            int x = Math.clamp(mouseX + 8, 0, Math.max(0, width - boxWidth));
            int y = Math.clamp(mouseY + 8, 0, Math.max(0, TabEditorScreen.this.height - 15));
            UiRender.roundedRect(graphics, x, y, boxWidth, 15, 3, SURFACE);
            graphics.drawString(font, title, x + 6, y + 4, TEXT, false);
        }

        private Placement at(double x, double y) {
            for (Placement placement : placements) {
                if (x >= placement.left() && x < placement.right() && y >= placement.top() && y < placement.bottom()) {
                    return placement;
                }
            }
            return null;
        }

        private Drop dropAt(double x, double y, Tab tab) {
            for (var item : areas.entrySet()) {
                HudLayout.Rect area = item.getValue();
                if (x < area.left() || x >= area.right() || y < area.top() || y >= area.bottom()) continue;
                Region region = item.getKey();
                Placement last = null;
                for (Placement placement : placements) {
                    if (placement.region() != region) continue;
                    if (y < (placement.top() + placement.bottom()) / 2.0) {
                        return new Drop(region, placement.index(), placement.top());
                    }
                    last = placement;
                }
                if (last != null) return new Drop(region, last.index() + 1, last.bottom());
                return new Drop(region, tab.entries(region).size(),
                        region.top() ? area.top() + 2 : Math.max(area.top(), area.bottom() - 3));
            }
            return null;
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
            pressed = at(x, y);
            if (pressed == null) return false;
            pressX = x;
            pressY = y;
            return true;
        }

        @Override
        public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
            if (pressed == null) return false;
            dragging |= Math.abs(x - pressX) + Math.abs(y - pressY) >= 3;
            return true;
        }

        @Override
        public boolean mouseReleased(double x, double y, int button) {
            if (pressed == null) return false;
            Placement source = pressed;
            Tab tab = currentTab();
            if (tab != null && dragging) {
                Drop drop = dropAt(x, y, tab);
                if (drop != null) update(current -> current.move(source.entry(), drop.region(), drop.index()));
            } else if (source.equals(at(x, y)) && y < source.top() + ROW
                    && (source.region().left() ? x >= source.right() - 12 : x < source.left() + 12)) {
                update(current -> current.remove(source.entry()));
            }
            pointerCancelled();
            return true;
        }

        @Override
        public void pointerCancelled() {
            pressed = null;
            dragging = false;
        }

        @Override
        public boolean mouseScrolled(double x, double y, double delta) {
            if (pages < 2 || delta == 0) return false;
            turnPage(delta > 0 ? -1 : 1);
            return true;
        }

        void turnPage(int direction) {
            page = Math.clamp(page + direction, 0, pages - 1);
        }
    }

    private record Group(String entry, int start, int end) {}
    private record Placement(String entry, Region region, int index, int left, int right, int top, int bottom) {}
    private record Drop(Region region, int index, int y) {}
}
