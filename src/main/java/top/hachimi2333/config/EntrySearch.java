package top.hachimi2333.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.hachimi2333.F3ather;
import top.hachimi2333.overlay.AddonEntries;
import top.hachimi2333.overlay.OverlaySettings.Tab;
import top.hachimi2333.overlay.VanillaEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import static top.hachimi2333.config.F3atherScreen.label;
import static top.hachimi2333.config.MinimalUi.*;

/**
 * Command-palette style modal for filtering, previewing, and adding debug entries to a custom tab.
 */
final class EntrySearch {
    private static final int HEADER_HEIGHT = 28;
    private static final int FILTER_HEIGHT = 24;
    private static final int ROW_HEIGHT = 34;
    private static final int ROW_GAP = 2;

    private final Font font;
    private final Scrollbar scrollbar = new Scrollbar();
    private final Scrollbar modMenuScrollbar = new Scrollbar();
    private final List<Choice> choices = new ArrayList<>();
    private final List<ModSource> modSources;
    private final boolean hasVanilla;
    private final Consumer<String> onAdd;
    private final Runnable onClose;
    private final EditBox input;
    private List<Choice> results = List.of();
    private FilterMode filterMode = FilterMode.ALL;
    private String selectedModId = "";
    private boolean modMenuOpen;
    private int modMenuScroll;
    private int modMenuMaxScroll;
    private int selected;
    private int scroll;
    private int maxScroll;
    private int screenWidth;
    private int screenHeight;

    EntrySearch(Font font, int width, int height, Tab tab, Consumer<String> onAdd, Runnable onClose) {
        this.font = font;
        this.onAdd = onAdd;
        this.onClose = onClose;

        boolean inWorld = isInWorld();
        String vanillaOwner = label("vanilla_owner").getString();
        String unknownMod = label("unknown_mod").getString();
        var mods = new LinkedHashMap<String, ModSource>();
        boolean vanillaPresent = false;

        for (VanillaEntry entry : VanillaEntry.values()) {
            if (tab.contains(entry.id())) continue;
            vanillaPresent = true;
            String fallback = joinPlain(F3ather.HUD.previewLines(entry.id(), false));
            String live = inWorld ? joinPlain(F3ather.HUD.previewLines(entry.id(), true)) : "";
            String searchKey = entry.name().replace('_', ' ') + " " + live + " " + fallback;
            choices.add(new Choice(entry.id(), Component.translatable(entry.translationKey()).getString(),
                    true, "", vanillaOwner, fallback, searchKey));
        }
        for (var entry : F3ather.ADDONS.entries()) {
            if (tab.contains(entry.id())) continue;
            String srcId = entry.source().id().isEmpty() ? "_unknown" : entry.source().id();
            String srcName = entry.source().name().isEmpty() ? unknownMod : entry.source().name();
            mods.putIfAbsent(srcId, new ModSource(srcId, srcName));
            String fallback = AddonEntries.stripFormatting(entry.sample());
            String live = inWorld ? joinPlain(F3ather.HUD.previewLines(entry.id(), true)) : "";
            String searchKey = entry.source().id() + " " + live + " " + fallback;
            choices.add(new Choice(entry.id(), entry.title(), false, srcId, srcName, fallback, searchKey));
        }

        this.hasVanilla = vanillaPresent;
        this.modSources = List.copyOf(mods.values());
        this.input = createPlainInput(font, 0, 0, 100, HEADER_HEIGHT, label("search"), 80);
        this.input.setResponder(ignored -> applyFilters(true));
        this.input.setFocused(true);
        layout(width, height);
        applyFilters(true);
    }

    EditBox input() { return input; }

    void layout(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
        Box box = box();
        input.setX(box.x() + 26);
        input.setY(baseline(font, box.y(), HEADER_HEIGHT));
        input.setWidth(Math.max(1, box.width() - 62));
        input.setHeight(Math.max(1, font.lineHeight + 2));
        updateScrollBounds();
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        Box box = box();
        graphics.fill(0, 0, screenWidth, screenHeight, MODAL_DIM);
        drawFloatingPanel(graphics, box.x(), box.y(), box.width(), box.height(), MODAL_BG, MODAL_BORDER);

        drawIcon(graphics, Icon.SEARCH, box.x() + 10, box.y() + (HEADER_HEIGHT - 9) / 2, ACCENT);
        input.render(graphics, mouseX, mouseY, delta);

        String count = Integer.toString(results.size());
        graphics.drawString(font, count, box.x() + box.width() - 10 - font.width(count),
                baseline(font, box.y(), HEADER_HEIGHT), TEXT_MUTED, false);

        graphics.fill(box.x() + 1, box.filterTop() - 1, box.x() + box.width() - 1, box.filterTop(), HAIRLINE);
        int uiMouseX = modMenuOpen || scrollbar.isDragging() ? -1 : mouseX;
        int uiMouseY = modMenuOpen || scrollbar.isDragging() ? -1 : mouseY;
        renderFilterBar(graphics, box, uiMouseX, uiMouseY);

        int listTop = box.listTop();
        int listHeight = box.listHeight();
        graphics.fill(box.x() + 1, listTop - 1, box.x() + box.width() - 1, listTop, HAIRLINE);

        int rowLeft = box.x() + 6;
        int rowWidth = box.width() - 12 - (maxScroll > 0 ? 7 : 0);
        boolean inWorld = isInWorld();
        Preview hoveredTooltip = null;
        int rowPitch = ROW_HEIGHT + ROW_GAP;

        graphics.enableScissor(box.x() + 1, listTop, box.x() + box.width() - 1, listTop + listHeight);
        if (results.isEmpty()) {
            String empty = label("no_results").getString();
            graphics.drawString(font, empty, box.x() + (box.width() - font.width(empty)) / 2,
                    baseline(font, listTop, Math.min(listHeight, 44)), TEXT_MUTED, false);
        } else {
            int y = listTop + 4 - scroll;
            for (int i = 0; i < results.size(); i++) {
                if (y + ROW_HEIGHT > listTop && y < listTop + listHeight) {
                    Choice choice = results.get(i);
                    boolean isSelected = i == selected;
                    boolean isHovered = uiMouseY >= listTop && uiMouseY < listTop + listHeight
                            && hit(uiMouseX, uiMouseY, rowLeft, y, rowWidth, ROW_HEIGHT);
                    if (isSelected) {
                        graphics.fill(rowLeft, y, rowLeft + rowWidth, y + ROW_HEIGHT, ACCENT_SOFT);
                        graphics.fill(rowLeft, y + 3, rowLeft + 2, y + ROW_HEIGHT - 3, ACCENT);
                    } else if (isHovered) {
                        graphics.fill(rowLeft, y, rowLeft + rowWidth, y + ROW_HEIGHT, ROW_HOVER);
                    }

                    int textLeft = rowLeft + 8;
                    int contentWidth = rowWidth - 16;
                    int badgeMax = Math.max(28, Math.min(110, contentWidth / 3));
                    String badgeText = ellipsize(font, choice.sourceLabel(), badgeMax - 8);
                    int badgeWidth = font.width(badgeText) + 8;
                    int badgeX = rowLeft + rowWidth - 8 - badgeWidth;
                    int badgeY = y + 4;
                    int titleMax = Math.max(20, badgeX - textLeft - 6);
                    String title = ellipsize(font, choice.title(), titleMax);

                    graphics.drawString(font, title, textLeft, y + 6, isSelected ? ACCENT : TEXT_PRIMARY, false);
                    drawSourceBadge(graphics, badgeX, badgeY, badgeWidth, badgeText, choice.vanilla());

                    Preview preview = resolvePreview(choice, inWorld);
                    String shownPreview = ellipsize(font, preview.summary(), contentWidth);
                    int previewColor = preview.empty() ? TEXT_DISABLED
                            : preview.sample() ? 0xff7b8888 : 0xffb4c4c3;
                    graphics.drawString(font, shownPreview, textLeft, y + 19, previewColor, false);

                    if (isHovered && (preview.lines().size() > 1
                            || font.width(preview.summary()) > contentWidth
                            || !title.equals(choice.title())
                            || !badgeText.equals(choice.sourceLabel()))) {
                        hoveredTooltip = preview;
                    }
                }
                y += rowPitch;
            }
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            scrollbar.render(graphics, box.x() + box.width() - 6, listTop + 4, listHeight - 8,
                    totalContentHeight(), scroll, modMenuOpen ? -1 : mouseX, modMenuOpen ? -1 : mouseY);
        }

        if (modMenuOpen) {
            renderModMenu(graphics, box, mouseX, mouseY);
        } else if (hoveredTooltip != null) {
            renderPreviewTooltip(graphics, hoveredTooltip, mouseX, mouseY);
        }
    }

    private void renderFilterBar(GuiGraphics graphics, Box box, int mouseX, int mouseY) {
        FilterLayout filters = filterLayout(box);
        for (FilterPill pill : filters.pills()) {
            boolean hovered = pill.enabled() && hit(mouseX, mouseY, pill.x(), pill.y(), pill.width(), pill.height());
            int fill = pill.active() ? ACCENT_SOFT : hovered ? ROW_HOVER : 0x00000000;
            int border = pill.active() ? ACCENT_BORDER : hovered ? GLASS_BORDER : HAIRLINE;
            int color = !pill.enabled() ? TEXT_DISABLED : pill.active() ? ACCENT : hovered ? TEXT_PRIMARY : TEXT_MUTED;
            if ((fill >>> 24) != 0) graphics.fill(pill.x(), pill.y(), pill.x() + pill.width(), pill.y() + pill.height(), fill);
            graphics.renderOutline(pill.x(), pill.y(), pill.width(), pill.height(), border);
            graphics.drawString(font, pill.label(), pill.x() + (pill.width() - font.width(pill.label())) / 2,
                    baseline(font, pill.y(), pill.height()), color, false);
        }

        if (filters.modSelector() != null) {
            ModSelector sel = filters.modSelector();
            boolean active = modMenuOpen || (filterMode == FilterMode.MODS && !selectedModId.isEmpty());
            boolean hovered = hit(mouseX, mouseY, sel.x(), sel.y(), sel.width(), sel.height());
            int fill = active ? ACCENT_SOFT : hovered ? SUBTLE_FILL : TRACK_BG;
            int border = active ? ACCENT_BORDER : hovered ? 0x44ffffff : GLASS_BORDER;
            int color = active ? ACCENT : hovered ? TEXT_PRIMARY : TEXT_MUTED;
            graphics.fill(sel.x(), sel.y(), sel.x() + sel.width(), sel.y() + sel.height(), fill);
            graphics.renderOutline(sel.x(), sel.y(), sel.width(), sel.height(), border);
            String label = ellipsize(font, sel.label(), Math.max(12, sel.width() - 18));
            graphics.drawString(font, label, sel.x() + 6, baseline(font, sel.y(), sel.height()), color, false);
            drawIcon(graphics, Icon.CHEVRON_DOWN, sel.x() + sel.width() - 13, sel.y() + (sel.height() - 9) / 2, color);
        }
    }

    private void drawSourceBadge(GuiGraphics graphics, int x, int y, int width, String label, boolean vanilla) {
        int fill = vanilla ? 0x16ffffff : 0x1e9ce2d8;
        int border = vanilla ? 0x26ffffff : 0x469ce2d8;
        int color = vanilla ? 0xffaebbbb : ACCENT;
        graphics.fill(x, y, x + width, y + 12, fill);
        graphics.renderOutline(x, y, width, 12, border);
        graphics.drawString(font, label, x + 4, y + 2, color, false);
    }

    private void renderModMenu(GuiGraphics graphics, Box box, int mouseX, int mouseY) {
        FilterLayout filters = filterLayout(box);
        if (filters.modSelector() == null) {
            closeModMenu();
            return;
        }
        List<ModSource> items = modMenuItems();
        ModMenuBox menu = modMenuBox(filters.modSelector(), items.size());
        modMenuMaxScroll = Math.max(0, menu.contentHeight() - menu.height());
        modMenuScroll = Math.clamp(modMenuScroll, 0, modMenuMaxScroll);

        drawFloatingPanel(graphics, menu.x(), menu.y(), menu.width(), menu.height(), MODAL_BG, MODAL_BORDER);
        int itemLeft = menu.x() + 4;
        int itemWidth = menu.width() - 8 - (modMenuMaxScroll > 0 ? 6 : 0);
        int listTop = menu.y() + 4;
        int listHeight = menu.height() - 8;

        graphics.enableScissor(menu.x() + 1, listTop, menu.x() + menu.width() - 1, listTop + listHeight);
        int y = listTop - modMenuScroll;
        for (ModSource item : items) {
            if (y + 18 > listTop && y < listTop + listHeight) {
                boolean active = filterMode == FilterMode.MODS && item.id().equals(selectedModId);
                boolean hovered = !modMenuScrollbar.isDragging() && hit(mouseX, mouseY, itemLeft, y, itemWidth, 18);
                if (active) {
                    graphics.fill(itemLeft, y, itemLeft + itemWidth, y + 18, ACCENT_SOFT);
                    graphics.fill(itemLeft, y + 3, itemLeft + 2, y + 15, ACCENT);
                } else if (hovered) {
                    graphics.fill(itemLeft, y, itemLeft + itemWidth, y + 18, ROW_HOVER);
                }
                String text = ellipsize(font, item.label(), Math.max(12, itemWidth - 10));
                graphics.drawString(font, text, itemLeft + 6, baseline(font, y, 18),
                        active ? ACCENT : hovered ? TEXT_PRIMARY : TEXT_MUTED, false);
            }
            y += 18;
        }
        graphics.disableScissor();

        if (modMenuMaxScroll > 0) {
            modMenuScrollbar.render(graphics, menu.x() + menu.width() - 5, listTop, listHeight,
                    menu.contentHeight(), modMenuScroll, mouseX, mouseY);
        }
    }

    private void renderPreviewTooltip(GuiGraphics graphics, Preview preview, int mouseX, int mouseY) {
        var lines = new ArrayList<Component>(preview.lines().size() + 1);
        lines.add(Component.literal(preview.title() + " · " + preview.source()));
        for (String line : preview.lines()) {
            lines.add(Component.literal(line));
        }
        graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        Box box = box();
        if (modMenuOpen) {
            return handleModMenuClick(box, mouseX, mouseY);
        }
        if (!hit(mouseX, mouseY, box.x(), box.y(), box.width(), box.height())) {
            onClose.run();
            return true;
        }
        if (hit(mouseX, mouseY, box.x(), box.y(), box.width(), HEADER_HEIGHT)) {
            input.setFocused(true);
            double clampedX = Math.clamp(mouseX, input.getX(), input.getX() + input.getWidth() - 1);
            double clampedY = Math.clamp(mouseY, input.getY(), input.getY() + input.getHeight() - 1);
            input.mouseClicked(clampedX, clampedY, button);
            return true;
        }
        if (hit(mouseX, mouseY, box.x(), box.filterTop(), box.width(), FILTER_HEIGHT)) {
            FilterLayout filters = filterLayout(box);
            for (FilterPill pill : filters.pills()) {
                if (pill.enabled() && hit(mouseX, mouseY, pill.x(), pill.y(), pill.width(), pill.height())) {
                    playClick();
                    filterMode = pill.mode();
                    if (filterMode != FilterMode.MODS) selectedModId = "";
                    applyFilters(true);
                    return true;
                }
            }
            if (filters.modSelector() != null) {
                ModSelector sel = filters.modSelector();
                if (hit(mouseX, mouseY, sel.x(), sel.y(), sel.width(), sel.height())) {
                    playClick();
                    modMenuOpen = !modMenuOpen;
                    modMenuScroll = 0;
                    modMenuScrollbar.reset();
                    return true;
                }
            }
            return true;
        }

        int listTop = box.listTop();
        int listHeight = box.listHeight();
        if (maxScroll > 0) {
            int nextScroll = scrollbar.mouseClicked(mouseX, mouseY, box.x() + box.width() - 6,
                    listTop + 4, listHeight - 8, totalContentHeight(), scroll);
            if (nextScroll >= 0) {
                scroll = nextScroll;
                return true;
            }
        }

        int rowLeft = box.x() + 6;
        int rowWidth = box.width() - 12 - (maxScroll > 0 ? 7 : 0);
        if (hit(mouseX, mouseY, rowLeft, listTop, rowWidth, listHeight)) {
            int relY = (int) Math.floor(mouseY - (listTop + 4 - scroll));
            int pitch = ROW_HEIGHT + ROW_GAP;
            int index = Math.floorDiv(relY, pitch);
            int offsetInRow = relY - index * pitch;
            if (relY >= 0 && offsetInRow < ROW_HEIGHT && index >= 0 && index < results.size()) {
                playClick();
                choose(results.get(index));
            }
        }
        return true;
    }

    private boolean handleModMenuClick(Box box, double mouseX, double mouseY) {
        FilterLayout filters = filterLayout(box);
        if (filters.modSelector() == null) {
            closeModMenu();
            return true;
        }
        ModSelector sel = filters.modSelector();
        if (hit(mouseX, mouseY, sel.x(), sel.y(), sel.width(), sel.height())) {
            playClick();
            closeModMenu();
            return true;
        }
        List<ModSource> items = modMenuItems();
        ModMenuBox menu = modMenuBox(sel, items.size());
        if (!hit(mouseX, mouseY, menu.x(), menu.y(), menu.width(), menu.height())) {
            closeModMenu();
            return true;
        }
        int listTop = menu.y() + 4;
        int listHeight = menu.height() - 8;
        if (modMenuMaxScroll > 0) {
            int nextScroll = modMenuScrollbar.mouseClicked(mouseX, mouseY, menu.x() + menu.width() - 5,
                    listTop, listHeight, menu.contentHeight(), modMenuScroll);
            if (nextScroll >= 0) {
                modMenuScroll = nextScroll;
                return true;
            }
        }
        int itemLeft = menu.x() + 4;
        int itemWidth = menu.width() - 8 - (modMenuMaxScroll > 0 ? 6 : 0);
        if (hit(mouseX, mouseY, itemLeft, listTop, itemWidth, listHeight)) {
            int index = (int) Math.floor((mouseY - (listTop - modMenuScroll)) / 18.0);
            if (index >= 0 && index < items.size()) {
                playClick();
                filterMode = FilterMode.MODS;
                selectedModId = items.get(index).id();
                closeModMenu();
                applyFilters(true);
            }
        }
        return true;
    }

    boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        Box box = box();
        if (modMenuOpen && modMenuScrollbar.isDragging()) {
            FilterLayout filters = filterLayout(box);
            if (filters.modSelector() != null) {
                ModMenuBox menu = modMenuBox(filters.modSelector(), modMenuItems().size());
                int nextScroll = modMenuScrollbar.mouseDragged(mouseY, menu.y() + 4, menu.height() - 8,
                        menu.contentHeight(), modMenuScroll);
                if (nextScroll >= 0) {
                    modMenuScroll = nextScroll;
                    return true;
                }
            }
            return false;
        }
        if (!scrollbar.isDragging()) return false;
        int nextScroll = scrollbar.mouseDragged(mouseY, box.listTop() + 4, box.listHeight() - 8,
                totalContentHeight(), scroll);
        if (nextScroll >= 0) {
            scroll = nextScroll;
            return true;
        }
        return false;
    }

    boolean mouseReleased() {
        boolean releasedMenu = modMenuScrollbar.mouseReleased();
        boolean releasedList = scrollbar.mouseReleased();
        return releasedMenu || releasedList;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (scrollY == 0) return true;
        Box box = box();
        if (modMenuOpen) {
            if (modMenuMaxScroll > 0) {
                FilterLayout filters = filterLayout(box);
                if (filters.modSelector() != null) {
                    ModMenuBox menu = modMenuBox(filters.modSelector(), modMenuItems().size());
                    if (hit(mouseX, mouseY, menu.x(), menu.y(), menu.width(), menu.height())) {
                        modMenuScroll = Math.clamp(modMenuScroll - (int) Math.round(scrollY * 18), 0, modMenuMaxScroll);
                    }
                }
            }
            return true;
        }
        if (maxScroll <= 0) return true;
        if (hit(mouseX, mouseY, box.x(), box.listTop(), box.width(), box.listHeight())) {
            scroll = Math.clamp(scroll - (int) Math.round(scrollY * (ROW_HEIGHT + ROW_GAP)), 0, maxScroll);
        }
        return true;
    }

    boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (modMenuOpen) {
                closeModMenu();
                return true;
            }
            onClose.run();
            return true;
        }
        if (modMenuOpen) return true;
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            if (!results.isEmpty()) {
                selected = Math.clamp(selected + (key == GLFW.GLFW_KEY_UP ? -1 : 1), 0, results.size() - 1);
                revealSelected();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (!results.isEmpty()) choose(results.get(selected));
            return true;
        }
        return input.keyPressed(key, scan, modifiers);
    }

    boolean charTyped(char character, int modifiers) {
        if (modMenuOpen) return false;
        return input.charTyped(character, modifiers);
    }

    private void applyFilters(boolean resetScroll) {
        String needle = input.getValue().strip().toLowerCase(Locale.ROOT);
        results = choices.stream()
                .filter(choice -> matchesFilter(choice) && choice.matches(needle))
                .toList();
        if (resetScroll) {
            selected = 0;
            scroll = 0;
        } else {
            selected = results.isEmpty() ? 0 : Math.clamp(selected, 0, results.size() - 1);
        }
        updateScrollBounds();
    }

    private boolean matchesFilter(Choice choice) {
        return switch (filterMode) {
            case ALL -> true;
            case VANILLA -> choice.vanilla();
            case MODS -> !choice.vanilla() && (selectedModId.isEmpty() || selectedModId.equals(choice.sourceId()));
        };
    }

    private int totalContentHeight() {
        if (results.isEmpty()) return 0;
        return results.size() * ROW_HEIGHT + (results.size() - 1) * ROW_GAP + 8;
    }

    private void updateScrollBounds() {
        int listHeight = box().listHeight();
        maxScroll = Math.max(0, totalContentHeight() - listHeight);
        scroll = Math.clamp(scroll, 0, maxScroll);
    }

    private void revealSelected() {
        int listHeight = box().listHeight();
        int pitch = ROW_HEIGHT + ROW_GAP;
        int top = selected * pitch;
        int bottom = top + ROW_HEIGHT + 8;
        if (top < scroll) scroll = top;
        else if (bottom - listHeight > scroll) scroll = bottom - listHeight;
        scroll = Math.clamp(scroll, 0, maxScroll);
    }

    private void choose(Choice choice) {
        onClose.run();
        onAdd.accept(choice.id());
    }

    private void closeModMenu() {
        modMenuOpen = false;
        modMenuScroll = 0;
        modMenuMaxScroll = 0;
        modMenuScrollbar.reset();
    }

    private FilterLayout filterLayout(Box box) {
        int y = box.filterTop() + (FILTER_HEIGHT - 16) / 2;
        int x = box.x() + 8;
        var pills = new ArrayList<FilterPill>(3);

        String allLabel = label("filter_all").getString();
        int allW = Math.max(28, font.width(allLabel) + 12);
        pills.add(new FilterPill(FilterMode.ALL, x, y, allW, 16, allLabel, filterMode == FilterMode.ALL, true));
        x += allW + 4;

        String vanillaLabel = label("filter_vanilla").getString();
        int vanillaW = Math.max(30, font.width(vanillaLabel) + 12);
        pills.add(new FilterPill(FilterMode.VANILLA, x, y, vanillaW, 16, vanillaLabel,
                filterMode == FilterMode.VANILLA, hasVanilla));
        x += vanillaW + 4;

        String modsLabel = label("filter_mods").getString();
        int modsW = Math.max(30, font.width(modsLabel) + 12);
        pills.add(new FilterPill(FilterMode.MODS, x, y, modsW, 16, modsLabel,
                filterMode == FilterMode.MODS && selectedModId.isEmpty(), !modSources.isEmpty()));
        x += modsW + 6;

        ModSelector selector = null;
        if (modSources.size() >= 2 && box.x() + box.width() - 8 - x >= 56) {
            String selLabel = label("filter_all_mods").getString();
            if (filterMode == FilterMode.MODS && !selectedModId.isEmpty()) {
                for (ModSource src : modSources) {
                    if (src.id().equals(selectedModId)) {
                        selLabel = src.label();
                        break;
                    }
                }
            }
            int avail = box.x() + box.width() - 8 - x;
            int selW = Math.min(avail, Math.max(76, Math.min(136, font.width(selLabel) + 22)));
            selector = new ModSelector(box.x() + box.width() - 8 - selW, y, selW, 16, selLabel);
        }
        return new FilterLayout(pills, selector);
    }

    private List<ModSource> modMenuItems() {
        var items = new ArrayList<ModSource>(modSources.size() + 1);
        items.add(new ModSource("", label("filter_all_mods").getString()));
        items.addAll(modSources);
        return items;
    }

    private ModMenuBox modMenuBox(ModSelector selector, int itemCount) {
        int menuW = Math.clamp(Math.max(selector.width(), 126), 96, Math.max(96, screenWidth - 16));
        int menuX = Math.clamp(selector.x() + selector.width() - menuW, 8, Math.max(8, screenWidth - 8 - menuW));
        int contentH = itemCount * 18 + 8;
        int belowSpace = screenHeight - (selector.y() + selector.height() + 8);
        int menuH = Math.min(contentH, Math.max(46, Math.min(belowSpace, 148)));
        int menuY = selector.y() + selector.height() + 3;
        return new ModMenuBox(menuX, menuY, menuW, menuH, contentH);
    }

    private Preview resolvePreview(Choice choice, boolean inWorld) {
        List<String> lines = inWorld ? stripAll(F3ather.HUD.previewLines(choice.id(), true)) : List.of();
        if (!lines.isEmpty()) {
            return new Preview(choice.title(), choice.sourceLabel(), String.join(" · ", lines), lines, false, false);
        }
        List<String> fallbackLines = choice.vanilla()
                ? stripAll(F3ather.HUD.previewLines(choice.id(), false))
                : (choice.fallbackSample().isEmpty() ? List.of() : List.of(choice.fallbackSample()));
        if (!fallbackLines.isEmpty()) {
            String prefixed = label("sample_prefix").getString() + " " + String.join(" · ", fallbackLines);
            var tip = new ArrayList<String>(fallbackLines.size());
            tip.add(label("sample_prefix").getString() + " " + fallbackLines.getFirst());
            for (int i = 1; i < fallbackLines.size(); i++) tip.add(fallbackLines.get(i));
            return new Preview(choice.title(), choice.sourceLabel(), prefixed, tip, true, false);
        }
        return new Preview(choice.title(), choice.sourceLabel(), "—", List.of(), true, true);
    }

    private static List<String> stripAll(List<String> raw) {
        if (raw.isEmpty()) return List.of();
        var clean = new ArrayList<String>(raw.size());
        for (String line : raw) {
            if (line == null) continue;
            String plain = AddonEntries.stripFormatting(line).strip();
            if (!plain.isEmpty()) clean.add(plain);
        }
        return clean;
    }

    private static String joinPlain(List<String> raw) {
        return String.join(" ", stripAll(raw));
    }

    private static boolean isInWorld() {
        return Minecraft.getInstance().level != null;
    }

    private Box box() {
        int w = Math.min(440, Math.max(220, screenWidth - 20));
        int x = (screenWidth - w) / 2;
        int y = Math.clamp(screenHeight / 7, 10, Math.max(10, screenHeight - 130));
        int filterTop = y + HEADER_HEIGHT + 1;
        int listTop = filterTop + FILTER_HEIGHT + 1;
        int listHeight = Math.clamp(screenHeight - listTop - 16, 68, 196);
        return new Box(x, y, w, HEADER_HEIGHT + 1 + FILTER_HEIGHT + 1 + listHeight, filterTop, listTop, listHeight);
    }

    private enum FilterMode { ALL, VANILLA, MODS }

    private record ModSource(String id, String label) {}
    private record FilterPill(FilterMode mode, int x, int y, int width, int height, String label,
                              boolean active, boolean enabled) {}
    private record ModSelector(int x, int y, int width, int height, String label) {}
    private record FilterLayout(List<FilterPill> pills, ModSelector modSelector) {}
    private record ModMenuBox(int x, int y, int width, int height, int contentHeight) {}
    private record Preview(String title, String source, String summary, List<String> lines,
                           boolean sample, boolean empty) {}
    private record Box(int x, int y, int width, int height, int filterTop, int listTop, int listHeight) {}

    private record Choice(String id, String title, boolean vanilla, String sourceId, String sourceLabel,
                          String fallbackSample, String searchKey) {
        boolean matches(String query) {
            if (query.isEmpty()) return true;
            return (title + " " + sourceLabel + " " + searchKey).toLowerCase(Locale.ROOT).contains(query);
        }
    }
}
