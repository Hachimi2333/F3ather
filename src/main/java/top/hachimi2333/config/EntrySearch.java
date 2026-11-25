package top.hachimi2333.config;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.hachimi2333.F3ather;
import top.hachimi2333.overlay.OverlaySettings.Tab;
import top.hachimi2333.overlay.VanillaEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static top.hachimi2333.config.F3atherScreen.label;
import static top.hachimi2333.config.MinimalUi.*;

/**
 * Command-palette style modal for searching and adding debug entries to a custom tab.
 */
final class EntrySearch {
    private static final int ROW_HEIGHT = 22;
    private static final int HEADER_HEIGHT = 28;

    private final Font font;
    private final Scrollbar scrollbar = new Scrollbar();
    private final List<Choice> choices = new ArrayList<>();
    private final Consumer<String> onAdd;
    private final Runnable onClose;
    private final EditBox input;
    private List<Choice> results = List.of();
    private int selected;
    private int scroll;
    private int maxScroll;
    private int screenWidth;
    private int screenHeight;

    EntrySearch(Font font, int width, int height, Tab tab, Consumer<String> onAdd, Runnable onClose) {
        this.font = font;
        this.onAdd = onAdd;
        this.onClose = onClose;

        for (VanillaEntry entry : VanillaEntry.values()) {
            if (tab.contains(entry.id())) continue;
            var examples = F3ather.HUD.previewLines(entry.id(), false);
            String alias = entry.name().replace('_', ' ') + " " + String.join(" ", examples);
            choices.add(new Choice(entry.id(), Component.translatable(entry.translationKey()).getString(), "", alias));
        }
        for (var entry : F3ather.ADDONS.entries()) {
            if (tab.contains(entry.id())) continue;
            choices.add(new Choice(entry.id(), entry.title(), entry.source().name(), entry.source().id() + " " + entry.sample()));
        }

        this.input = createPlainInput(font, 0, 0, 100, HEADER_HEIGHT, label("search"), 80);
        this.input.setResponder(this::filter);
        this.input.setFocused(true);
        layout(width, height);
        filter("");
    }

    EditBox input() { return input; }

    void layout(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
        Box box = box();
        input.setX(box.x() + 26);
        input.setY(baseline(font, box.y(), HEADER_HEIGHT));
        input.setWidth(Math.max(1, box.width() - 56));
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

        int listTop = box.listTop();
        int listHeight = box.listHeight();
        graphics.fill(box.x() + 1, listTop - 1, box.x() + box.width() - 1, listTop, HAIRLINE);

        int rowLeft = box.x() + 6;
        int rowWidth = box.width() - 12 - (maxScroll > 0 ? 6 : 0);
        graphics.enableScissor(box.x() + 1, listTop, box.x() + box.width() - 1, listTop + listHeight);
        if (results.isEmpty()) {
            String empty = label("no_results").getString();
            graphics.drawString(font, empty, box.x() + (box.width() - font.width(empty)) / 2,
                    baseline(font, listTop, Math.min(listHeight, 40)), TEXT_MUTED, false);
        } else {
            int y = listTop + 4 - scroll;
            for (int i = 0; i < results.size(); i++) {
                if (y + ROW_HEIGHT > listTop && y < listTop + listHeight) {
                    Choice choice = results.get(i);
                    boolean isSelected = i == selected;
                    boolean isHovered = hit(mouseX, mouseY, rowLeft, y, rowWidth, ROW_HEIGHT);
                    if (isSelected) {
                        graphics.fill(rowLeft, y, rowLeft + rowWidth, y + ROW_HEIGHT, ACCENT_SOFT);
                        graphics.fill(rowLeft, y + 2, rowLeft + 2, y + ROW_HEIGHT - 2, ACCENT);
                    } else if (isHovered) {
                        graphics.fill(rowLeft, y, rowLeft + rowWidth, y + ROW_HEIGHT, ROW_HOVER);
                    }

                    int textArea = rowWidth - 16;
                    String owner = ellipsize(font, choice.owner(), textArea / 3);
                    int ownerWidth = owner.isEmpty() ? 0 : font.width(owner) + 8;
                    String title = ellipsize(font, choice.title(), textArea - ownerWidth);
                    int baseline = baseline(font, y, ROW_HEIGHT);
                    graphics.drawString(font, title, rowLeft + 8, baseline,
                            isSelected ? ACCENT : TEXT_PRIMARY, false);
                    if (!owner.isEmpty()) {
                        graphics.drawString(font, owner, rowLeft + rowWidth - 8 - font.width(owner),
                                baseline, TEXT_MUTED, false);
                    }
                }
                y += ROW_HEIGHT;
            }
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            scrollbar.render(graphics, box.x() + box.width() - 6, listTop + 4, listHeight - 8,
                    results.size() * ROW_HEIGHT + 8, scroll, mouseX, mouseY);
        }
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        Box box = box();
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
        int listTop = box.listTop();
        int listHeight = box.listHeight();
        if (maxScroll > 0) {
            int nextScroll = scrollbar.mouseClicked(mouseX, mouseY, box.x() + box.width() - 6,
                    listTop + 4, listHeight - 8, results.size() * ROW_HEIGHT + 8, scroll);
            if (nextScroll >= 0) {
                scroll = nextScroll;
                return true;
            }
        }
        int rowLeft = box.x() + 6;
        int rowWidth = box.width() - 12 - (maxScroll > 0 ? 6 : 0);
        if (hit(mouseX, mouseY, rowLeft, listTop, rowWidth, listHeight)) {
            int index = (int) Math.floor((mouseY - (listTop + 4 - scroll)) / ROW_HEIGHT);
            if (index >= 0 && index < results.size()) {
                playClick();
                choose(results.get(index));
            }
        }
        return true;
    }

    boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !scrollbar.isDragging()) return false;
        Box box = box();
        int nextScroll = scrollbar.mouseDragged(mouseY, box.listTop() + 4, box.listHeight() - 8,
                results.size() * ROW_HEIGHT + 8, scroll);
        if (nextScroll >= 0) {
            scroll = nextScroll;
            return true;
        }
        return false;
    }

    boolean mouseReleased() {
        return scrollbar.mouseReleased();
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (maxScroll <= 0 || scrollY == 0) return true;
        Box box = box();
        if (hit(mouseX, mouseY, box.x(), box.listTop(), box.width(), box.listHeight())) {
            scroll = Math.clamp(scroll - (int) Math.round(scrollY * ROW_HEIGHT), 0, maxScroll);
        }
        return true;
    }

    boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose.run();
            return true;
        }
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
        return input.charTyped(character, modifiers);
    }

    private void filter(String query) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        results = choices.stream().filter(choice -> choice.matches(needle)).toList();
        selected = 0;
        scroll = 0;
        updateScrollBounds();
    }

    private void updateScrollBounds() {
        int listHeight = box().listHeight();
        int contentHeight = results.isEmpty() ? 0 : results.size() * ROW_HEIGHT + 8;
        maxScroll = Math.max(0, contentHeight - listHeight);
        scroll = Math.clamp(scroll, 0, maxScroll);
    }

    private void revealSelected() {
        int listHeight = box().listHeight();
        int top = selected * ROW_HEIGHT;
        int bottom = top + ROW_HEIGHT + 8;
        if (top < scroll) scroll = top;
        else if (bottom - listHeight > scroll) scroll = bottom - listHeight;
        scroll = Math.clamp(scroll, 0, maxScroll);
    }

    private void choose(Choice choice) {
        onClose.run();
        onAdd.accept(choice.id());
    }

    private Box box() {
        int w = Math.min(356, Math.max(180, screenWidth - 20));
        int x = (screenWidth - w) / 2;
        int y = Math.clamp(screenHeight / 6, 12, Math.max(12, screenHeight - 100));
        int listHeight = Math.clamp(screenHeight - y - HEADER_HEIGHT - 24, 44, 154);
        return new Box(x, y, w, HEADER_HEIGHT + 1 + listHeight, y + HEADER_HEIGHT + 1, listHeight);
    }

    private record Box(int x, int y, int width, int height, int listTop, int listHeight) {}

    private record Choice(String id, String title, String owner, String alias) {
        boolean matches(String query) {
            return (title + " " + owner + " " + alias).toLowerCase(Locale.ROOT).contains(query);
        }
    }
}
