package top.hachimi2333.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.hachimi2333.overlay.OverlaySettings;
import top.hachimi2333.overlay.OverlaySettings.BuiltIn;
import top.hachimi2333.overlay.OverlaySettings.Tab;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static top.hachimi2333.config.MinimalUi.*;

public final class F3atherScreen extends Screen {
    private static final int HEADER_HEIGHT = 34;
    private static final int CLOSE_SIZE = 20;

    private final Screen parent;
    private final Scrollbar scrollbar = new Scrollbar();
    private Page activePage = Page.GENERAL;
    private double scroll;
    private int maxScroll;
    private Modal modal;
    private EditBox nameBox;
    private Component tooltip;

    public F3atherScreen(Screen parent) {
        super(label("title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        if (modal instanceof NameModal nameModal) {
            attachNameBox(nameModal.draft());
        } else {
            nameBox = null;
        }
    }

    private int panelWidth() { return Math.clamp(width - 20, Math.min(220, width), 404); }
    private int panelHeight() { return Math.clamp(height - 20, Math.min(156, height), 282); }
    private int panelLeft() { return (width - panelWidth()) / 2; }
    private int panelTop() { return (height - panelHeight()) / 2; }
    private int pad() { return panelWidth() < 300 ? 10 : 16; }
    private int bodyTop() { return panelTop() + HEADER_HEIGHT + 8; }
    private int bodyBottom() { return panelTop() + panelHeight() - pad(); }
    private int bodyLeft() { return panelLeft() + pad(); }
    private int bodyWidth() { return panelWidth() - 2 * pad(); }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (minecraft.level == null) renderPanorama(graphics, delta);
        graphics.flush();
        renderBlurredBackground(delta);
        graphics.fill(0, 0, width, height, BACKDROP_DIM);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        tooltip = null;
        super.render(graphics, mouseX, mouseY, delta);

        int pLeft = panelLeft();
        int pTop = panelTop();
        int pWidth = panelWidth();
        int pHeight = panelHeight();
        int uiMouseX = modal == null && !scrollbar.isDragging() ? mouseX : -1;
        int uiMouseY = modal == null && !scrollbar.isDragging() ? mouseY : -1;

        drawFloatingPanel(graphics, pLeft, pTop, pWidth, pHeight, GLASS_BG, GLASS_BORDER);
        renderHeader(graphics, uiMouseX, uiMouseY, pLeft, pTop, pWidth);
        graphics.fill(pLeft + pad(), pTop + HEADER_HEIGHT, pLeft + pWidth - pad(), pTop + HEADER_HEIGHT + 1, HAIRLINE);

        int bLeft = bodyLeft();
        int bTop = bodyTop();
        int bWidth = bodyWidth();
        int bHeight = Math.max(1, bodyBottom() - bTop);

        List<RowItem> items = buildItems();
        int contentHeight = totalHeight(items);
        maxScroll = Math.max(0, contentHeight - bHeight);
        scroll = Math.clamp(scroll, 0, maxScroll);
        int rowWidth = maxScroll > 0 ? bWidth - 7 : bWidth;

        graphics.enableScissor(bLeft, bTop, bLeft + bWidth, bTop + bHeight);
        int y = bTop - (int) Math.round(scroll);
        for (RowItem item : items) {
            int h = item.height();
            if (y + h > bTop && y < bTop + bHeight) {
                item.render(graphics, bLeft, y, rowWidth, uiMouseX, uiMouseY);
            }
            y += h + item.gap();
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            scrollbar.render(graphics, bLeft + bWidth - 2, bTop, bHeight, contentHeight, (int) Math.round(scroll),
                    modal == null ? mouseX : -1, modal == null ? mouseY : -1);
        }

        if (modal != null) {
            renderModal(graphics, mouseX, mouseY, delta);
        } else if (tooltip != null) {
            graphics.renderTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY, int left, int top, int panelW) {
        int pad = pad();
        int centerY = top + HEADER_HEIGHT / 2;
        graphics.blit(LOGO, left + pad, centerY - 7, 0, 0, 14, 14, 14, 14);
        int titleX = left + pad + 20;
        if (panelW >= 290) {
            graphics.drawString(font, "F3ather", titleX, baseline(font, top, HEADER_HEIGHT), TEXT_PRIMARY, false);
        }

        int closeX = closeButtonX(left, panelW);
        int closeY = centerY - CLOSE_SIZE / 2;
        boolean closeHover = hit(mouseX, mouseY, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE);
        drawIconButton(graphics, Icon.CLOSE, closeX, closeY, CLOSE_SIZE, closeHover, false, false);
        if (closeHover) tooltip = label("back");

        SegmentedLayout seg = segmentedLayout();
        graphics.fill(seg.x(), seg.y(), seg.x() + seg.width(), seg.y() + seg.height(), TRACK_BG);
        graphics.renderOutline(seg.x(), seg.y(), seg.width(), seg.height(), GLASS_BORDER);

        drawSegment(graphics, seg.genX(), seg.y() + 2, seg.segWidth(), seg.height() - 4,
                label("general").getString(), activePage == Page.GENERAL,
                hit(mouseX, mouseY, seg.genX(), seg.y() + 2, seg.segWidth(), seg.height() - 4));
        drawSegment(graphics, seg.tabsX(), seg.y() + 2, seg.segWidth(), seg.height() - 4,
                label("tabs").getString(), activePage == Page.TABS,
                hit(mouseX, mouseY, seg.tabsX(), seg.y() + 2, seg.segWidth(), seg.height() - 4));
    }

    private void drawSegment(GuiGraphics graphics, int x, int y, int width, int height,
                             String label, boolean active, boolean hovered) {
        if (active) {
            graphics.fill(x, y, x + width, y + height, ACCENT_SOFT);
            graphics.renderOutline(x, y, width, height, ACCENT_BORDER);
        } else if (hovered) {
            graphics.fill(x, y, x + width, y + height, ROW_HOVER);
        }
        int color = active ? ACCENT : hovered ? TEXT_PRIMARY : TEXT_MUTED;
        String text = ellipsize(font, label, width - 8);
        graphics.drawString(font, text, x + (width - font.width(text)) / 2, baseline(font, y, height), color, false);
    }

    private SegmentedLayout segmentedLayout() {
        int segWidth = Math.clamp(Math.max(font.width(label("general")), font.width(label("tabs"))) + 18, 46, 84);
        int totalW = segWidth * 2 + 6;
        int totalH = 22;
        int pLeft = panelLeft();
        int pWidth = panelWidth();
        int minX = pLeft + pad() + (pWidth >= 290 ? 68 : 20);
        int maxX = pLeft + pWidth - pad() - 26 - totalW;
        int x = Math.clamp(pLeft + (pWidth - totalW) / 2, minX, Math.max(minX, maxX));
        int y = panelTop() + (HEADER_HEIGHT - totalH) / 2;
        return new SegmentedLayout(x, y, totalW, totalH, segWidth, x + 2, x + 4 + segWidth);
    }

    private List<RowItem> buildItems() {
        var settings = ClientConfig.current();
        var items = new ArrayList<RowItem>();
        if (activePage == Page.GENERAL) {
            items.add(new RefreshItem(settings.refreshHz()));
            items.add(new PreferenceItem(
                    label("hide_tab_bar").getString(),
                    label("hide_tab_bar.desc").getString(),
                    settings.hideTabBar(),
                    value -> ClientConfig.save(ClientConfig.current().withHideTabBar(value))));
            items.add(new PreferenceItem(
                    label("compatibility").getString(),
                    label("compatibility.desc").getString(),
                    settings.compatibility(),
                    value -> ClientConfig.save(ClientConfig.current().withCompatibility(value))));
            items.add(new PreferenceItem(
                    label("disable_animations").getString(),
                    label("disable_animations.desc").getString(),
                    settings.disableAnimations(),
                    value -> ClientConfig.save(ClientConfig.current().withDisableAnimations(value))));
        } else {
            items.add(new TabsActionHeader());
            for (BuiltIn tab : BuiltIn.values()) {
                items.add(new BuiltInTabItem(
                        Component.translatable("f3ather.tab." + tab.id()).getString(),
                        settings.enabled().contains(tab),
                        value -> ClientConfig.save(ClientConfig.current().withBuiltIn(tab, value))));
            }
            items.add(new SectionLabelItem(label("custom_tabs").getString()));
            if (settings.customTabs().isEmpty()) {
                items.add(new EmptyNoticeItem(label("empty_tabs").getString()));
            } else {
                for (Tab tab : settings.customTabs()) {
                    items.add(new CustomTabItem(tab));
                }
            }
        }
        return items;
    }

    private static int totalHeight(List<RowItem> items) {
        int total = 0;
        for (int i = 0; i < items.size(); i++) {
            total += items.get(i).height();
            if (i + 1 < items.size()) total += items.get(i).gap();
        }
        return total;
    }

    private int closeButtonX(int panelLeft, int panelWidth) {
        return panelLeft + panelWidth - pad() - CLOSE_SIZE;
    }

    private int closeButtonY(int panelTop) {
        return panelTop + HEADER_HEIGHT / 2 - CLOSE_SIZE / 2;
    }

    private void renderModal(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, MODAL_DIM);
        ModalRect box = modalRect();
        drawFloatingPanel(graphics, box.x(), box.y(), box.width(), box.height(), MODAL_BG, MODAL_BORDER);

        if (modal instanceof NameModal nameModal) {
            NameModalLayout layout = nameModalLayout(box, nameModal);
            String title = label(nameModal.isNew() ? "new_tab" : "rename").getString();
            graphics.drawString(font, title, box.x() + 14, box.y() + 12, TEXT_PRIMARY, false);

            boolean focused = nameBox != null && nameBox.isFocused();
            drawInputFrame(graphics, layout.inputX(), layout.inputY(), layout.inputW(), layout.inputH(), focused);
            if (nameBox != null) nameBox.render(graphics, mouseX, mouseY, delta);

            drawButton(graphics, font, layout.cancelX(), layout.btnY(), layout.cancelW(), 20, layout.cancelText(),
                    ButtonStyle.GHOST, hit(mouseX, mouseY, layout.cancelX(), layout.btnY(), layout.cancelW(), 20),
                    false, true);
            drawButton(graphics, font, layout.primaryX(), layout.btnY(), layout.primaryW(), 20, layout.primaryText(),
                    ButtonStyle.ACCENT, hit(mouseX, mouseY, layout.primaryX(), layout.btnY(), layout.primaryW(), 20),
                    false, !nameModal.draft().isBlank());
        } else if (modal instanceof DeleteModal deleteModal) {
            DeleteModalLayout layout = deleteModalLayout(box);
            String prompt = ellipsize(font, label("delete_confirm", deleteModal.tab().name()).getString(), box.width() - 28);
            graphics.drawString(font, prompt, box.x() + 14, box.y() + 14, TEXT_PRIMARY, false);

            drawButton(graphics, font, layout.cancelX(), layout.btnY(), layout.cancelW(), 20, layout.cancelText(),
                    ButtonStyle.GHOST, hit(mouseX, mouseY, layout.cancelX(), layout.btnY(), layout.cancelW(), 20),
                    false, true);
            drawButton(graphics, font, layout.deleteX(), layout.btnY(), layout.deleteW(), 20, layout.deleteText(),
                    ButtonStyle.DANGER, hit(mouseX, mouseY, layout.deleteX(), layout.btnY(), layout.deleteW(), 20),
                    false, true);
        }
    }

    private ModalRect modalRect() {
        int w = Math.min(modal instanceof NameModal ? 264 : 248, width - 24);
        int h = modal instanceof NameModal ? 98 : 74;
        return new ModalRect((width - w) / 2, (height - h) / 2, w, h);
    }

    private NameModalLayout nameModalLayout(ModalRect box, NameModal nameModal) {
        String primaryText = label(nameModal.isNew() ? "create" : "save").getString();
        String cancelText = label("cancel").getString();
        int primaryW = Math.max(52, font.width(primaryText) + 18);
        int cancelW = Math.max(48, font.width(cancelText) + 16);
        int btnY = box.y() + box.height() - 32;
        int primaryX = box.x() + box.width() - 14 - primaryW;
        int cancelX = primaryX - 6 - cancelW;
        return new NameModalLayout(box.x() + 14, box.y() + 30, box.width() - 28, 24,
                btnY, cancelX, cancelW, primaryX, primaryW, cancelText, primaryText);
    }

    private DeleteModalLayout deleteModalLayout(ModalRect box) {
        String deleteText = label("delete").getString();
        String cancelText = label("cancel").getString();
        int deleteW = Math.max(52, font.width(deleteText) + 18);
        int cancelW = Math.max(48, font.width(cancelText) + 16);
        int btnY = box.y() + box.height() - 32;
        int deleteX = box.x() + box.width() - 14 - deleteW;
        int cancelX = deleteX - 6 - cancelW;
        return new DeleteModalLayout(btnY, cancelX, cancelW, deleteX, deleteW, cancelText, deleteText);
    }

    private void openNameModal(String tabId) {
        Tab existing = tabId == null ? null : ClientConfig.current().customTab(tabId);
        if (tabId != null && existing == null) return;
        String initial = existing == null ? "" : existing.name();
        var nameModal = new NameModal(tabId, initial);
        modal = nameModal;
        attachNameBox(initial);
    }

    private void attachNameBox(String value) {
        ModalRect box = modalRect();
        int inputX = box.x() + 14;
        int inputY = box.y() + 30;
        int inputW = box.width() - 28;
        int inputH = 24;
        nameBox = createPlainInput(font, inputX, inputY, inputW, inputH, label("tab_name"), 40);
        nameBox.setValue(value);
        nameBox.setResponder(text -> {
            if (modal instanceof NameModal current) modal = new NameModal(current.tabId(), text);
        });
        setFocused(nameBox);
        nameBox.setFocused(true);
    }

    private void closeModal() {
        modal = null;
        nameBox = null;
        setFocused(null);
    }

    private void submitNameModal() {
        if (!(modal instanceof NameModal nameModal)) return;
        String cleaned = nameModal.draft().strip();
        if (cleaned.isEmpty()) return;
        closeModal();
        if (nameModal.isNew()) {
            Tab created = Tab.create(cleaned);
            ClientConfig.save(ClientConfig.current().withTab(created));
            openEditor(created.id());
        } else {
            changeTab(nameModal.tabId(), tab -> tab.rename(cleaned));
        }
    }

    private void openDeleteModal(Tab tab) {
        nameBox = null;
        modal = new DeleteModal(tab);
    }

    private void confirmDeleteModal() {
        if (!(modal instanceof DeleteModal deleteModal)) return;
        ClientConfig.save(ClientConfig.current().withoutTab(deleteModal.tab().id()));
        closeModal();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mouseX, mouseY, button);
        if (modal != null) return handleModalClick(mouseX, mouseY, button);

        int closeX = closeButtonX(panelLeft(), panelWidth());
        int closeY = closeButtonY(panelTop());
        if (hit(mouseX, mouseY, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE)) {
            playClick();
            onClose();
            return true;
        }

        SegmentedLayout seg = segmentedLayout();
        if (hit(mouseX, mouseY, seg.genX(), seg.y() + 2, seg.segWidth(), seg.height() - 4)) {
            if (activePage != Page.GENERAL) {
                playClick();
                activePage = Page.GENERAL;
                scroll = 0;
                scrollbar.reset();
            }
            return true;
        }
        if (hit(mouseX, mouseY, seg.tabsX(), seg.y() + 2, seg.segWidth(), seg.height() - 4)) {
            if (activePage != Page.TABS) {
                playClick();
                activePage = Page.TABS;
                scroll = 0;
                scrollbar.reset();
            }
            return true;
        }

        int bLeft = bodyLeft();
        int bTop = bodyTop();
        int bWidth = bodyWidth();
        int bHeight = Math.max(1, bodyBottom() - bTop);
        if (hit(mouseX, mouseY, bLeft, bTop, bWidth, bHeight)) {
            List<RowItem> items = buildItems();
            int contentHeight = totalHeight(items);
            if (maxScroll > 0) {
                int nextScroll = scrollbar.mouseClicked(mouseX, mouseY, bLeft + bWidth - 2, bTop, bHeight,
                        contentHeight, scroll);
                if (nextScroll >= 0) {
                    scroll = nextScroll;
                    return true;
                }
            }
            int rowWidth = maxScroll > 0 ? bWidth - 7 : bWidth;
            int y = bTop - (int) Math.round(scroll);
            for (RowItem item : items) {
                int h = item.height();
                if (hit(mouseX, mouseY, bLeft, y, rowWidth, h)) {
                    if (item.click(bLeft, y, rowWidth, mouseX, mouseY)) return true;
                }
                y += h + item.gap();
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (modal == null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && scrollbar.isDragging()) {
            int bTop = bodyTop();
            int bHeight = Math.max(1, bodyBottom() - bTop);
            int nextScroll = scrollbar.mouseDragged(mouseY, bTop, bHeight, totalHeight(buildItems()), scroll);
            if (nextScroll >= 0) {
                scroll = nextScroll;
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scrollbar.mouseReleased()) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean handleModalClick(double mouseX, double mouseY, int button) {
        ModalRect box = modalRect();
        if (!hit(mouseX, mouseY, box.x(), box.y(), box.width(), box.height())) {
            closeModal();
            return true;
        }
        if (modal instanceof NameModal nameModal) {
            NameModalLayout layout = nameModalLayout(box, nameModal);
            if (nameBox != null && hit(mouseX, mouseY, layout.inputX(), layout.inputY(), layout.inputW(), layout.inputH())) {
                setFocused(nameBox);
                nameBox.setFocused(true);
                double clampedX = Math.clamp(mouseX, nameBox.getX(), nameBox.getX() + nameBox.getWidth() - 1);
                double clampedY = Math.clamp(mouseY, nameBox.getY(), nameBox.getY() + nameBox.getHeight() - 1);
                nameBox.mouseClicked(clampedX, clampedY, button);
                return true;
            }
            if (hit(mouseX, mouseY, layout.cancelX(), layout.btnY(), layout.cancelW(), 20)) {
                playClick();
                closeModal();
                return true;
            }
            if (!nameModal.draft().isBlank() && hit(mouseX, mouseY, layout.primaryX(), layout.btnY(), layout.primaryW(), 20)) {
                playClick();
                submitNameModal();
                return true;
            }
            return true;
        } else if (modal instanceof DeleteModal) {
            DeleteModalLayout layout = deleteModalLayout(box);
            if (hit(mouseX, mouseY, layout.cancelX(), layout.btnY(), layout.cancelW(), 20)) {
                playClick();
                closeModal();
                return true;
            }
            if (hit(mouseX, mouseY, layout.deleteX(), layout.btnY(), layout.deleteW(), 20)) {
                playClick();
                confirmDeleteModal();
                return true;
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (modal != null || maxScroll <= 0 || scrollY == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (hit(mouseX, mouseY, bodyLeft(), bodyTop(), bodyWidth(), Math.max(1, bodyBottom() - bodyTop()))) {
            scroll = Math.clamp(scroll - scrollY * 18, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (modal != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                closeModal();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (modal instanceof NameModal) submitNameModal();
                else if (modal instanceof DeleteModal) confirmDeleteModal();
                return true;
            }
            if (nameBox != null && nameBox.keyPressed(key, scan, modifiers)) return true;
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            activePage = key == GLFW.GLFW_KEY_LEFT ? Page.GENERAL : Page.TABS;
            scroll = 0;
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (modal != null) {
            return nameBox != null && nameBox.charTyped(character, modifiers);
        }
        return super.charTyped(character, modifiers);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void changeTab(String id, UnaryOperator<Tab> change) {
        var settings = ClientConfig.current();
        var tab = settings.customTab(id);
        if (tab != null) ClientConfig.save(settings.withTab(change.apply(tab)));
    }

    private void openEditor(String id) {
        minecraft.setScreen(new TabEditorScreen(this, id));
    }

    static Component label(String key, Object... arguments) {
        return Component.translatable("f3ather.screen." + key, arguments);
    }

    private static Component rateLabel(int hz) {
        return hz == 0 ? label("unlimited") : label("hz", hz);
    }

    private enum Page { GENERAL, TABS }

    private record SegmentedLayout(int x, int y, int width, int height, int segWidth, int genX, int tabsX) {}
    private record ModalRect(int x, int y, int width, int height) {}
    private record NameModalLayout(int inputX, int inputY, int inputW, int inputH, int btnY,
                                   int cancelX, int cancelW, int primaryX, int primaryW,
                                   String cancelText, String primaryText) {}
    private record DeleteModalLayout(int btnY, int cancelX, int cancelW, int deleteX, int deleteW,
                                     String cancelText, String deleteText) {}

    private sealed interface Modal permits NameModal, DeleteModal {}
    private record NameModal(String tabId, String draft) implements Modal {
        boolean isNew() { return tabId == null; }
    }
    private record DeleteModal(Tab tab) implements Modal {}

    private interface RowItem {
        int height();
        default int gap() { return 3; }
        void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY);
        default boolean click(int x, int y, int width, double mouseX, double mouseY) { return false; }
    }

    private final class RefreshItem implements RowItem {
        private final int currentHz;

        RefreshItem(int currentHz) { this.currentHz = currentHz; }

        @Override public int height() { return 38; }

        private int pillWidth(int rowWidth) {
            return rowWidth < 250 ? 18 : 22;
        }

        private int barWidth(int rowWidth) {
            return pillWidth(rowWidth) * OverlaySettings.REFRESH_PRESETS.size() + 4;
        }

        @Override
        public void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
            boolean rowHover = hit(mouseX, mouseY, x, y, width, height());
            if (rowHover) graphics.fill(x, y, x + width, y + height(), ROW_HOVER);

            int segW = pillWidth(width);
            int barW = barWidth(width);
            int barH = 20;
            int barX = x + width - 8 - barW;
            int barY = y + (height() - barH) / 2;
            int textMax = Math.max(20, barX - (x + 8) - 8);

            String title = ellipsize(font, label("refresh").getString(), textMax);
            String desc = ellipsize(font, label("refresh.desc").getString(), textMax);
            graphics.drawString(font, title, x + 8, y + 8, TEXT_PRIMARY, false);
            graphics.drawString(font, desc, x + 8, y + 21, TEXT_MUTED, false);

            graphics.fill(barX, barY, barX + barW, barY + barH, TRACK_BG);
            graphics.renderOutline(barX, barY, barW, barH, GLASS_BORDER);

            int sx = barX + 2;
            for (int preset : OverlaySettings.REFRESH_PRESETS) {
                boolean active = preset == currentHz;
                boolean hover = hit(mouseX, mouseY, sx, barY + 2, segW, barH - 4);
                if (active) {
                    graphics.fill(sx, barY + 2, sx + segW, barY + barH - 2, ACCENT_SOFT);
                    graphics.renderOutline(sx, barY + 2, segW, barH - 4, ACCENT_BORDER);
                } else if (hover) {
                    graphics.fill(sx, barY + 2, sx + segW, barY + barH - 2, ROW_HOVER);
                }
                String tag = preset == 0 ? "∞" : Integer.toString(preset);
                int color = active ? ACCENT : hover ? TEXT_PRIMARY : TEXT_MUTED;
                graphics.drawString(font, tag, sx + (segW - font.width(tag)) / 2,
                        baseline(font, barY + 2, barH - 4), color, false);
                if (hover) tooltip = rateLabel(preset);
                sx += segW;
            }
        }

        @Override
        public boolean click(int x, int y, int width, double mouseX, double mouseY) {
            int segW = pillWidth(width);
            int barW = barWidth(width);
            int barH = 20;
            int barX = x + width - 8 - barW;
            int barY = y + (height() - barH) / 2;
            int sx = barX + 2;
            for (int preset : OverlaySettings.REFRESH_PRESETS) {
                if (hit(mouseX, mouseY, sx, barY + 2, segW, barH - 4)) {
                    playClick();
                    ClientConfig.save(ClientConfig.current().withRefresh(preset));
                    return true;
                }
                sx += segW;
            }
            return false;
        }
    }

    private final class PreferenceItem implements RowItem {
        private final String title;
        private final String description;
        private final boolean value;
        private final Consumer<Boolean> onToggle;

        PreferenceItem(String title, String description, boolean value, Consumer<Boolean> onToggle) {
            this.title = title;
            this.description = description;
            this.value = value;
            this.onToggle = onToggle;
        }

        @Override public int height() { return 34; }

        @Override
        public void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
            boolean hovered = hit(mouseX, mouseY, x, y, width, height());
            if (hovered) graphics.fill(x, y, x + width, y + height(), ROW_HOVER);

            int switchX = x + width - 8 - SWITCH_WIDTH;
            int switchY = y + (height() - SWITCH_HEIGHT) / 2;
            int textMax = Math.max(20, switchX - (x + 8) - 10);

            graphics.drawString(font, ellipsize(font, title, textMax), x + 8, y + 7, TEXT_PRIMARY, false);
            graphics.drawString(font, ellipsize(font, description, textMax), x + 8, y + 19, TEXT_MUTED, false);
            drawSwitch(graphics, switchX, switchY, value, hovered, false);
        }

        @Override
        public boolean click(int x, int y, int width, double mouseX, double mouseY) {
            playClick();
            onToggle.accept(!value);
            return true;
        }
    }

    private final class TabsActionHeader implements RowItem {
        @Override public int height() { return 24; }
        @Override public int gap() { return 4; }

        private int buttonWidth() {
            return Math.max(54, font.width(label("new_tab")) + 16);
        }

        @Override
        public void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
            graphics.drawString(font, label("builtin_tabs"), x + 4, baseline(font, y, height()), TEXT_MUTED, false);
            int btnW = buttonWidth();
            int btnX = x + width - btnW;
            int btnY = y + (height() - 20) / 2;
            drawButton(graphics, font, btnX, btnY, btnW, 20, label("new_tab").getString(), ButtonStyle.ACCENT,
                    hit(mouseX, mouseY, btnX, btnY, btnW, 20), false, true);
        }

        @Override
        public boolean click(int x, int y, int width, double mouseX, double mouseY) {
            int btnW = buttonWidth();
            int btnX = x + width - btnW;
            int btnY = y + (height() - 20) / 2;
            if (hit(mouseX, mouseY, btnX, btnY, btnW, 20)) {
                playClick();
                openNameModal(null);
                return true;
            }
            return false;
        }
    }

    private final class BuiltInTabItem implements RowItem {
        private final String name;
        private final boolean enabled;
        private final Consumer<Boolean> onToggle;

        BuiltInTabItem(String name, boolean enabled, Consumer<Boolean> onToggle) {
            this.name = name;
            this.enabled = enabled;
            this.onToggle = onToggle;
        }

        @Override public int height() { return 25; }
        @Override public int gap() { return 2; }

        @Override
        public void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
            boolean hovered = hit(mouseX, mouseY, x, y, width, height());
            if (hovered) graphics.fill(x, y, x + width, y + height(), ROW_HOVER);

            int switchX = x + width - 8 - SWITCH_WIDTH;
            int switchY = y + (height() - SWITCH_HEIGHT) / 2;
            String label = ellipsize(font, name, Math.max(20, switchX - (x + 8) - 8));
            graphics.drawString(font, label, x + 8, baseline(font, y, height()),
                    enabled ? TEXT_PRIMARY : TEXT_MUTED, false);
            drawSwitch(graphics, switchX, switchY, enabled, hovered, false);
        }

        @Override
        public boolean click(int x, int y, int width, double mouseX, double mouseY) {
            playClick();
            onToggle.accept(!enabled);
            return true;
        }
    }

    private final class SectionLabelItem implements RowItem {
        private final String title;

        SectionLabelItem(String title) { this.title = title; }

        @Override public int height() { return 22; }
        @Override public int gap() { return 3; }

        @Override
        public void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
            graphics.fill(x, y + 5, x + width, y + 6, HAIRLINE);
            graphics.drawString(font, title, x + 4, y + 11, TEXT_MUTED, false);
        }
    }

    private final class EmptyNoticeItem implements RowItem {
        private final String message;

        EmptyNoticeItem(String message) { this.message = message; }

        @Override public int height() { return 26; }
        @Override public int gap() { return 0; }

        @Override
        public void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
            String text = ellipsize(font, message, Math.max(20, width - 16));
            graphics.drawString(font, text, x + 8, baseline(font, y, height()), TEXT_DISABLED, false);
        }
    }

    private final class CustomTabItem implements RowItem {
        private static final int BTN_SIZE = 19;
        private final Tab tab;

        CustomTabItem(Tab tab) { this.tab = tab; }

        @Override public int height() { return 27; }
        @Override public int gap() { return 2; }

        @Override
        public void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
            boolean rowHover = hit(mouseX, mouseY, x, y, width, height());
            if (rowHover) graphics.fill(x, y, x + width, y + height(), ROW_HOVER);

            int switchX = x + width - 8 - SWITCH_WIDTH;
            int switchY = y + (height() - SWITCH_HEIGHT) / 2;
            int delX = switchX - 8 - BTN_SIZE;
            int renX = delX - 2 - BTN_SIZE;
            int editX = renX - 2 - BTN_SIZE;
            int btnY = y + (height() - BTN_SIZE) / 2;

            String name = ellipsize(font, tab.name(), Math.max(20, editX - (x + 8) - 8));
            graphics.drawString(font, name, x + 8, baseline(font, y, height()),
                    tab.enabled() ? TEXT_PRIMARY : TEXT_MUTED, false);

            boolean editHover = hit(mouseX, mouseY, editX, btnY, BTN_SIZE, BTN_SIZE);
            boolean renHover = hit(mouseX, mouseY, renX, btnY, BTN_SIZE, BTN_SIZE);
            boolean delHover = hit(mouseX, mouseY, delX, btnY, BTN_SIZE, BTN_SIZE);
            boolean switchHover = hit(mouseX, mouseY, switchX - 2, y, SWITCH_WIDTH + 6, height());

            drawIconButton(graphics, Icon.EDIT, editX, btnY, BTN_SIZE, editHover, false, false);
            drawIconButton(graphics, Icon.RENAME, renX, btnY, BTN_SIZE, renHover, false, false);
            drawIconButton(graphics, Icon.DELETE, delX, btnY, BTN_SIZE, delHover, false, true);
            drawSwitch(graphics, switchX, switchY, tab.enabled(), switchHover, false);

            if (editHover) tooltip = label("edit");
            else if (renHover) tooltip = label("rename");
            else if (delHover) tooltip = label("delete");
        }

        @Override
        public boolean click(int x, int y, int width, double mouseX, double mouseY) {
            int switchX = x + width - 8 - SWITCH_WIDTH;
            int delX = switchX - 8 - BTN_SIZE;
            int renX = delX - 2 - BTN_SIZE;
            int editX = renX - 2 - BTN_SIZE;
            int btnY = y + (height() - BTN_SIZE) / 2;

            if (hit(mouseX, mouseY, switchX - 2, y, SWITCH_WIDTH + 6, height())) {
                playClick();
                changeTab(tab.id(), current -> current.toggle(!current.enabled()));
                return true;
            }
            if (hit(mouseX, mouseY, delX, btnY, BTN_SIZE, BTN_SIZE)) {
                playClick();
                openDeleteModal(tab);
                return true;
            }
            if (hit(mouseX, mouseY, renX, btnY, BTN_SIZE, BTN_SIZE)) {
                playClick();
                openNameModal(tab.id());
                return true;
            }
            if (hit(mouseX, mouseY, editX, btnY, BTN_SIZE, BTN_SIZE) || mouseX < editX) {
                playClick();
                openEditor(tab.id());
                return true;
            }
            return false;
        }
    }
}
