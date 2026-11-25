package top.hachimi2333.overlay;

import org.junit.jupiter.api.Test;
import top.hachimi2333.overlay.OverlaySettings.Region;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class OverlayMotionTest {
    private static final int WIDTH = 320;
    private static final long MS = 1_000_000L;

    @Test
    void openingSlidesInFromAllThreeEdgesAndRestartsAfterReset() {
        var motion = new OverlayMotion();
        var first = motion.position(-200 * MS, false, WIDTH);
        assertEquals(-WIDTH, first.columnX(true));
        assertEquals(WIDTH, first.columnX(false));
        assertEquals(-HudLayout.HEADER, first.headerY());
        var midway = motion.position(-100 * MS, false, WIDTH);
        assertEquals(-40, midway.columnX(true));
        assertEquals(40, midway.columnX(false));
        assertTrue(midway.headerY() > first.headerY());
        assertEquals(HudLayout.Position.EXPANDED, motion.position(0, false, WIDTH));

        motion.reset();
        assertEquals(first, motion.position(10_000 * MS, false, WIDTH));
    }

    @Test
    void reflowOccursAfterEachTransitionAndUsesTheReclaimedSpace() {
        var motion = new OverlayMotion();
        motion.position(0, false, WIDTH);
        assertEquals(HudLayout.Position.EXPANDED, motion.position(3999 * MS, false, WIDTH));
        var closing = motion.position(4080 * MS, false, WIDTH);
        assertEquals(9, closing.headerHeight());
        assertEquals(HudLayout.HEADER, closing.layoutHeader());
        var hidden = motion.position(4160 * MS, false, WIDTH);
        assertEquals(0, hidden.headerHeight());
        assertEquals(0, hidden.layoutHeader());

        var rows = Map.of(Region.BOTTOM_RIGHT, List.of("0", "1", "2", "3", "4", "5"));
        assertEquals(2, HudLayout.pageCount(WIDTH, closing.layoutHeader(), 116, 116, rows, true));
        assertEquals(1, HudLayout.pageCount(WIDTH, hidden.layoutHeader(), 116, 116, rows, true));
        var shownArea = HudLayout.bounds(WIDTH, closing.layoutHeader(), 116, Region.BOTTOM_RIGHT, rows, true);
        var hiddenArea = HudLayout.bounds(WIDTH, hidden.layoutHeader(), 116, Region.BOTTOM_RIGHT, rows, true);
        assertEquals(shownArea.firstY(false, 1), hiddenArea.firstY(false, 1));

        motion.reveal(5000 * MS, false);
        assertEquals(hidden, motion.position(5000 * MS, false, WIDTH));
        assertEquals(new HudLayout.Position(0, 9, 0), motion.position(5080 * MS, false, WIDTH));
        assertEquals(HudLayout.Position.EXPANDED, motion.position(5160 * MS, false, WIDTH));
    }

    @Test
    void activityReversesTheSlideFromItsCurrentPositionAndRestartsTheTimeout() {
        var motion = new OverlayMotion();
        motion.position(0, false, WIDTH);
        var closing = motion.position(4080 * MS, false, WIDTH);
        motion.reveal(4080 * MS, false);
        assertEquals(closing, motion.position(4080 * MS, false, WIDTH));
        assertEquals(HudLayout.Position.EXPANDED, motion.position(4240 * MS, false, WIDTH));
        motion.reveal(6000 * MS, false);
        assertEquals(HudLayout.Position.EXPANDED, motion.position(9999 * MS, false, WIDTH));
        assertEquals(0, motion.position(10_160 * MS, false, WIDTH).headerHeight());
    }

    @Test
    void disablingMotionSettlesImmediatelyWithoutDisablingAutoHide() {
        var motion = new OverlayMotion();
        assertEquals(HudLayout.Position.EXPANDED, motion.position(0, true, WIDTH));
        assertEquals(new HudLayout.Position(0, 0, 0), motion.position(4000 * MS, true, WIDTH));
        motion.reveal(5000 * MS, true);
        assertEquals(HudLayout.Position.EXPANDED, motion.position(5000 * MS, true, WIDTH));

        assertEquals(9, motion.position(9080 * MS, false, WIDTH).headerHeight());
        assertEquals(new HudLayout.Position(0, 0, 0), motion.position(9080 * MS, true, WIDTH));
        assertEquals(0, motion.position(9081 * MS, false, WIDTH).headerHeight());
    }

    @Test
    void closingSlidesOutAndAllowsReopeningMidway() {
        var motion = new OverlayMotion();
        motion.open(0, false);
        motion.close(1000 * MS, false);
        assertEquals(HudLayout.Position.EXPANDED, motion.exitPosition(1000 * MS, false, WIDTH));
        var exiting = motion.exitPosition(1150 * MS, false, WIDTH);
        assertTrue(exiting.columnDistance() > 0);
        assertTrue(exiting.headerHeight() < HudLayout.HEADER);
        assertEquals(HudLayout.HEADER, exiting.layoutHeader());

        motion.open(1150 * MS, false);
        assertEquals(exiting, motion.position(1150 * MS, false, WIDTH));
        assertNull(motion.exitPosition(1151 * MS, false, WIDTH));
        var reopening = motion.position(1200 * MS, false, WIDTH);
        motion.close(1200 * MS, false);
        assertEquals(reopening, motion.exitPosition(1200 * MS, false, WIDTH));
        assertNull(motion.exitPosition(1400 * MS, false, WIDTH));
    }

    @Test
    void closingKeepsAnIdleHeaderHiddenAndDisablingMotionSkipsTheExit() {
        var motion = new OverlayMotion();
        motion.open(0, false);
        motion.close(5000 * MS, false);
        var exiting = motion.exitPosition(5100 * MS, false, WIDTH);
        assertEquals(0, exiting.headerHeight());
        assertEquals(0, exiting.layoutHeader());
        assertNull(motion.exitPosition(5101 * MS, true, WIDTH));

        motion.open(5200 * MS, true);
        motion.close(5300 * MS, true);
        assertNull(motion.exitPosition(5300 * MS, true, WIDTH));
        motion.open(5400 * MS, false);
        assertEquals(WIDTH, motion.position(5400 * MS, false, WIDTH).columnDistance());
    }

    @Test
    void lockedTabHidesTabBarAndDisablesCyclingWithoutChangingMotion() {
        var entering = new OverlayMotion().position(0, false, WIDTH);
        var hidden = entering.withHeaderHidden(true);
        assertEquals(WIDTH, hidden.columnDistance());
        assertEquals(0, hidden.headerHeight());
        assertEquals(0, hidden.layoutHeader());

        var settings = OverlaySettings.DEFAULT.withLockedTab("performance").withDisableAnimations(true);
        var hud = new OverlayHud(new AddonEntries());
        hud.accept(hud.column(true, java.util.List.of()), hud.column(false, java.util.List.of()), settings, false);
        assertEquals("performance", hud.activeTab());
        hud.cycle(settings);
        assertEquals("performance", hud.activeTab());
        assertEquals(HudLayout.Position.preview(true), hud.position(settings, WIDTH));
        assertEquals(HudLayout.Position.EXPANDED, hud.position(settings.withLockedTab(""), WIDTH));
        hud.cycle(settings.withLockedTab(""));
        assertEquals("world", hud.activeTab());
    }
}
