package top.hachimi2333.config;

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
import static top.hachimi2333.config.MinimalUi.*;

public final class TabEditorScreen extends Screen {
    private static final int ROW = HudLayout.ROW_HEIGHT;
    private static final int DOCK_HEIGHT = 28;

    private final Screen parent;
    private final String id;
    private final EnumMap<Region, HudLayout.Rect> areas = new EnumMap<>(Region.class);
    private List<Placement> placements = List.of();
    private Placement pressed;
    private boolean dragging;
    private double pressX;
    private double pressY;
    private int page;
    private int pages = 1;
    private EntrySearch search;
    private boolean suppressSlash;

    public TabEditorScreen(Screen parent, String id) {
        super(label("edit"));
        this.parent = parent;
        this.id = id;
    }

    @Override
    protected void init() {
        clearWidgets();
        if (search != null) {
            search.layout(width, height);
            setFocused(search.input());
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (minecraft.level == null) renderPanorama(graphics, delta);
    }

    @Override
    @SuppressWarnings("deprecation") // Minecraft 1.21.1 uses the same managed batch for debug text.
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        Tab tab = currentTab();
        if (tab == null) {
            onClose();
            return;
        }
        F3ather.HUD.updateEditorPreview(minecraft, graphics);
        super.render(graphics, mouseX, mouseY, delta);

        renderPreview(graphics, tab, mouseX, mouseY);
        renderDock(graphics, tab, search == null ? mouseX : -1, search == null ? mouseY : -1);
        if (search != null) {
            search.render(graphics, mouseX, mouseY, delta);
        }
    }

    private void renderPreview(GuiGraphics graphics, Tab tab, int mouseX, int mouseY) {
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

        var settings = ClientConfig.current();
        String locked = settings.effectiveLockedTab(F3ather.HUD.modsDetected());
        var position = HudLayout.Position.preview(tab.id().equals(locked));
        int top = position.layoutHeader();
        var bottom = F3ather.HUD.bottoms(height, top);
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
            F3ather.HUD.drawPreview(graphics, settings, tab, position, bottom, rows, page, pages);
            if (search == null) drawInteractionGuides(graphics, mouseX, mouseY, tab);
        });
    }

    private void drawInteractionGuides(GuiGraphics graphics, int mouseX, int mouseY, Tab tab) {
        Dock dock = dock();
        boolean overDock = hit(mouseX, mouseY, dock.x(), dock.y(), dock.width(), dock.height());
        Placement hovered = overDock ? null : at(mouseX, mouseY);

        if (!dragging && hovered != null) {
            graphics.fill(hovered.left(), hovered.top(), hovered.right(), hovered.bottom(), 0x189ce2d8);
            graphics.renderOutline(hovered.left(), hovered.top(), hovered.right() - hovered.left(),
                    hovered.bottom() - hovered.top(), 0x999ce2d8);
            boolean overRemove = isOverRemove(hovered, mouseX, mouseY);
            int actionX = hovered.region().left() ? hovered.right() - 10 : hovered.left() + 2;
            graphics.drawString(font, "×", actionX, hovered.top() + 1, overRemove ? DANGER : ACCENT, false);
        }

        if (!dragging || pressed == null) return;
        Drop target = dropAt(mouseX, mouseY, tab);
        for (var entry : areas.entrySet()) {
            Region region = entry.getKey();
            HudLayout.Rect area = entry.getValue();
            boolean active = target != null && target.region() == region;
            if (active) {
                graphics.fill(area.left() + 1, area.top(), area.right() - 1, area.bottom(), 0x149ce2d8);
            }
            graphics.renderOutline(area.left() + 1, area.top(), area.right() - area.left() - 2,
                    area.bottom() - area.top(), active ? 0x999ce2d8 : 0x22ffffff);
        }
        if (target != null) {
            var area = areas.get(target.region());
            graphics.fill(area.left() + 4, target.y(), area.right() - 4, target.y() + 2, ACCENT);
            graphics.fill(area.left() + 3, target.y() - 1, area.left() + 5, target.y() + 3, ACCENT);
            graphics.fill(area.right() - 5, target.y() - 1, area.right() - 3, target.y() + 3, ACCENT);
        }

        String title = font.plainSubstrByWidth(entryTitle(pressed.entry()), Math.max(16, width / 2 - 14));
        int chipWidth = font.width(title) + 14;
        int chipX = Math.clamp(mouseX + 8, 0, Math.max(0, width - chipWidth));
        int chipY = Math.clamp(mouseY + 8, 0, Math.max(0, height - 16));
        drawFloatingPanel(graphics, chipX, chipY, chipWidth, 16, MODAL_BG, ACCENT_BORDER);
        graphics.drawString(font, title, chipX + 7, baseline(font, chipY, 16), TEXT_PRIMARY, false);
    }

    private void renderDock(GuiGraphics graphics, Tab tab, int mouseX, int mouseY) {
        Dock dock = dock();
        drawFloatingPanel(graphics, dock.x(), dock.y(), dock.width(), dock.height(), GLASS_BG, GLASS_BORDER);

        drawButton(graphics, font, dock.backX(), dock.btnY(), dock.btnW(), dock.btnH(),
                label("back").getString(), ButtonStyle.GHOST,
                hit(mouseX, mouseY, dock.backX(), dock.btnY(), dock.btnW(), dock.btnH()), false, true);
        drawButton(graphics, font, dock.addX(), dock.btnY(), dock.btnW(), dock.btnH(),
                label("add_entry").getString(), ButtonStyle.ACCENT,
                hit(mouseX, mouseY, dock.addX(), dock.btnY(), dock.btnW(), dock.btnH()), false, true);

        PagerLayout pager = pagerLayout(dock, tab);
        if (pager != null) {
            if (!pager.title().isEmpty()) {
                graphics.drawString(font, pager.title(), pager.titleX(), baseline(font, dock.y(), dock.height()),
                        TEXT_PRIMARY, false);
            }
            drawIconButton(graphics, Icon.CHEVRON_LEFT, pager.prevX(), pager.arrowY(), 13,
                    page > 0 && hit(mouseX, mouseY, pager.prevX(), pager.arrowY(), 13, 15), false, false);
            graphics.drawString(font, pager.pageText(), pager.prevX() + 13, baseline(font, dock.y(), dock.height()),
                    TEXT_MUTED, false);
            drawIconButton(graphics, Icon.CHEVRON_RIGHT, pager.nextX(), pager.arrowY(), 13,
                    page < pages - 1 && hit(mouseX, mouseY, pager.nextX(), pager.arrowY(), 13, 15), false, false);
        } else {
            int centerLeft = dock.backX() + dock.btnW() + 6;
            int centerW = Math.max(0, dock.addX() - 6 - centerLeft);
            String title = ellipsize(font, tab.name(), centerW);
            graphics.drawString(font, title, centerLeft + (centerW - font.width(title)) / 2,
                    baseline(font, dock.y(), dock.height()), TEXT_PRIMARY, false);
        }

        if (!dragging && search == null && dock.y() + dock.height() + 20 < height) {
            String hint = ellipsize(font, label("editor_hint").getString(), Math.max(60, width - 32));
            int hintW = font.width(hint) + 14;
            int hintX = (width - hintW) / 2;
            int hintY = dock.y() + dock.height() + 6;
            drawGlassPanel(graphics, hintX, hintY, hintW, 14, 0xb810141a, 0x20ffffff);
            graphics.drawString(font, hint, hintX + 7, baseline(font, hintY, 14), 0xffc8d4d4, false);
        }
    }

    private PagerLayout pagerLayout(Dock dock, Tab tab) {
        if (pages <= 1) return null;
        int centerLeft = dock.backX() + dock.btnW() + 6;
        int centerW = Math.max(0, dock.addX() - 6 - centerLeft);
        if (centerW < 68) return null;
        String pageText = Component.translatable("f3ather.hud.page", page + 1, pages).getString();
        int pagerW = font.width(pageText) + 26;
        String title = ellipsize(font, tab.name(), Math.max(0, centerW - pagerW - 4));
        int combinedW = font.width(title) + (title.isEmpty() ? 0 : 4) + pagerW;
        int startX = centerLeft + Math.max(0, (centerW - combinedW) / 2);
        int prevX = startX + font.width(title) + (title.isEmpty() ? 0 : 4);
        int nextX = prevX + 13 + font.width(pageText);
        int arrowY = dock.y() + (dock.height() - 15) / 2;
        return new PagerLayout(title, startX, pageText, prevX, nextX, arrowY);
    }

    private Dock dock() {
        int dockW = Math.min(256, Math.max(180, width - 24));
        int dockX = (width - dockW) / 2;
        int dockY = (height - DOCK_HEIGHT) / 2;
        int btnW = Math.max(font.width(label("back")), font.width(label("add_entry"))) + 14;
        btnW = Math.min(btnW, (dockW - 40) / 2);
        int btnH = 20;
        int btnY = dockY + (DOCK_HEIGHT - btnH) / 2;
        return new Dock(dockX, dockY, dockW, DOCK_HEIGHT, dockX + 5, dockX + dockW - 5 - btnW, btnY, btnW, btnH);
    }

    private void openSearch() {
        Tab tab = currentTab();
        if (tab == null || search != null) return;
        search = new EntrySearch(font, width, height, tab,
                entry -> update(current -> current.move(entry, Region.TOP_LEFT, current.entries(Region.TOP_LEFT).size())),
                () -> {
                    search = null;
                    setFocused(null);
                });
        setFocused(search.input());
    }

    private static boolean isOverRemove(Placement placement, double x, double y) {
        return y < placement.top() + ROW
                && (placement.region().left() ? x >= placement.right() - 12 : x < placement.left() + 12);
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
        if (search != null) return search.mouseClicked(x, y, button);
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(x, y, button);

        Dock dock = dock();
        if (hit(x, y, dock.backX(), dock.btnY(), dock.btnW(), dock.btnH())) {
            playClick();
            onClose();
            return true;
        }
        if (hit(x, y, dock.addX(), dock.btnY(), dock.btnW(), dock.btnH())) {
            playClick();
            openSearch();
            return true;
        }
        if (hit(x, y, dock.x(), dock.y(), dock.width(), dock.height())) {
            Tab tab = currentTab();
            PagerLayout pager = tab == null ? null : pagerLayout(dock, tab);
            if (pager != null) {
                if (hit(x, y, pager.prevX(), pager.arrowY(), 13, 15)) {
                    turnPage(-1);
                } else if (hit(x, y, pager.nextX(), pager.arrowY(), 13, 15)) {
                    turnPage(1);
                }
            }
            return true;
        }

        pressed = at(x, y);
        if (pressed != null) {
            pressX = x;
            pressY = y;
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (search != null) return search.mouseDragged(x, y, button);
        if (pressed == null) return super.mouseDragged(x, y, button, dx, dy);
        dragging |= Math.abs(x - pressX) + Math.abs(y - pressY) >= 3;
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (search != null) return search.mouseReleased();
        if (pressed == null) return super.mouseReleased(x, y, button);
        Placement source = pressed;
        Tab tab = currentTab();
        if (tab != null && dragging) {
            Drop drop = dropAt(x, y, tab);
            if (drop != null) update(current -> current.move(source.entry(), drop.region(), drop.index()));
        } else if (source.equals(at(x, y)) && isOverRemove(source, x, y)) {
            playClick();
            update(current -> current.remove(source.entry()));
        }
        pressed = null;
        dragging = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (search != null) return search.mouseScrolled(x, y, scrollY);
        if (pages >= 2 && scrollY != 0) {
            turnPage(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (search != null) return search.keyPressed(key, scan, modifiers);
        if (key == GLFW.GLFW_KEY_SLASH) {
            suppressSlash = true;
            openSearch();
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            turnPage(key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (suppressSlash) {
            suppressSlash = false;
            if (character == '/') return true;
        }
        if (search != null) return search.charTyped(character, modifiers);
        return super.charTyped(character, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_SLASH) suppressSlash = false;
        return super.keyReleased(key, scan, modifiers);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    private void turnPage(int direction) {
        page = Math.clamp(page + direction, 0, pages - 1);
    }

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

    private record Dock(int x, int y, int width, int height, int backX, int addX, int btnY, int btnW, int btnH) {}
    private record PagerLayout(String title, int titleX, String pageText, int prevX, int nextX, int arrowY) {}
    private record Group(String entry, int start, int end) {}
    private record Placement(String entry, Region region, int index, int left, int right, int top, int bottom) {}
    private record Drop(Region region, int index, int y) {}
}
