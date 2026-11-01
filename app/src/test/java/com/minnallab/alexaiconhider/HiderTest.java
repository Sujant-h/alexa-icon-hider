package com.minnallab.alexaiconhider;

import static com.minnallab.alexaiconhider.Hider.HISTORY_SIZE;
import static com.minnallab.alexaiconhider.Hider.LOOP_WINDOW_MS;
import static com.minnallab.alexaiconhider.Hider.LOOP_WRITES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Locale;

import org.junit.Test;

/** The parts of Hider that need no Android: loop guard, history trimming, history lines. */
public class HiderTest {
    private static final long T = 1_793_000_000_000L; // some wall-clock time in 2026

    /** Feeds the times to recentWrites() one by one, as apply() does on each write. */
    private static String writesAt(long... times) {
        String writes = "";
        for (long t : times) writes = Hider.recentWrites(writes, t);
        return writes;
    }

    /** count writes, step ms apart, the last one at end. */
    private static long[] every(long step, int count, long end) {
        long[] times = new long[count];
        for (int i = 0; i < count; i++) times[i] = end - (count - 1 - i) * step;
        return times;
    }

    // Loop guard

    @Test
    public void firstWriteStartsTheList() {
        assertEquals(String.valueOf(T), Hider.recentWrites("", T));
        assertFalse(Hider.isLoop(Hider.recentWrites("", T), T));
    }

    @Test
    public void newestFirst() {
        assertEquals((T + 2) + "," + (T + 1) + "," + T, writesAt(T, T + 1, T + 2));
    }

    @Test
    public void keepsOnlyTheNewestLoopWrites() {
        long[] times = every(1_000, LOOP_WRITES + 5, T);
        String[] kept = writesAt(times).split(",");
        assertEquals(LOOP_WRITES, kept.length);
        assertEquals(String.valueOf(T), kept[0]);
        assertEquals(String.valueOf(times[5]), kept[LOOP_WRITES - 1]);
    }

    @Test
    public void normalBootIsNoLoop() {
        // Two writes per boot: LOCKED_BOOT_COMPLETED and the reset on first unlock.
        assertFalse(Hider.isLoop(writesAt(T, T + 5_400), T + 5_400));
    }

    @Test
    public void tenthWriteWithinTheWindowIsALoop() {
        long[] times = every(1_500, LOOP_WRITES, T);
        String writes = "";
        for (int i = 0; i < times.length; i++) {
            writes = Hider.recentWrites(writes, times[i]);
            assertEquals("write " + (i + 1), i == LOOP_WRITES - 1, Hider.isLoop(writes, times[i]));
        }
    }

    /** One write at oldest, then nine more in the last 8 s up to T. */
    private static String tenWritesSince(long oldest) {
        String writes = Hider.recentWrites("", oldest);
        for (long t : every(1_000, LOOP_WRITES - 1, T)) writes = Hider.recentWrites(writes, t);
        return writes;
    }

    @Test
    public void windowIsExclusive() {
        assertTrue(Hider.isLoop(tenWritesSince(T - LOOP_WINDOW_MS + 1), T));
        assertFalse(Hider.isLoop(tenWritesSince(T - LOOP_WINDOW_MS), T));
    }

    @Test
    public void slowResetsNeverLoop() {
        // Ten writes 7 s apart span 63 s, so a steady slow reset is never caught.
        long[] times = every(7_000, 50, T);
        String writes = "";
        for (long t : times) {
            writes = Hider.recentWrites(writes, t);
            assertFalse(Hider.isLoop(writes, t));
        }
    }

    @Test
    public void clockSetBackDropsLaterWrites() {
        String before = writesAt(every(1_000, LOOP_WRITES - 1, T));
        long back = T - 3_600_000;
        assertEquals(String.valueOf(back), Hider.recentWrites(before, back));
        assertFalse(Hider.isLoop(Hider.recentWrites(before, back), back));
    }

    @Test
    public void clockSetBackKeepsEarlierWrites() {
        String before = writesAt(T - 10_000, T - 5_000, T + 5_000);
        assertEquals(T + "," + (T - 5_000) + "," + (T - 10_000), Hider.recentWrites(before, T));
    }

    @Test
    public void clockSetBackStillCatchesALoopAfterwards() {
        String before = writesAt(every(1_000, LOOP_WRITES - 1, T + 3_600_000));
        long[] times = every(1_000, LOOP_WRITES, T);
        String writes = before;
        for (long t : times) writes = Hider.recentWrites(writes, t);
        assertTrue(Hider.isLoop(writes, T));
    }

    @Test
    public void writesFromAnEarlierBootDoNotCount() {
        // Nine writes an hour ago, then a reboot: one more write is not ten within the window.
        String earlier = writesAt(every(1_000, LOOP_WRITES - 1, T - 3_600_000));
        assertFalse(Hider.isLoop(Hider.recentWrites(earlier, T), T));
    }

    @Test
    public void skipsEmptyEntries() {
        assertEquals((T + 10) + "," + T, Hider.recentWrites(",," + T + ",", T + 10));
    }

    // History

    @Test
    public void prependToEmptyHistory() {
        assertEquals("a", Hider.prepend("", "a"));
    }

    @Test
    public void prependPutsNewestFirst() {
        assertEquals("c\nb\na", Hider.prepend("b\na", "c"));
    }

    @Test
    public void prependKeepsTheNewestHistorySize() {
        String history = "";
        for (int i = 1; i <= HISTORY_SIZE + 5; i++) history = Hider.prepend(history, "line " + i);
        String[] lines = history.split("\n");
        assertEquals(HISTORY_SIZE, lines.length);
        assertEquals("line " + (HISTORY_SIZE + 5), lines[0]);
        assertEquals("line 6", lines[HISTORY_SIZE - 1]);
    }

    @Test
    public void prependSkipsEmptyLines() {
        assertEquals("c\nb", Hider.prepend("\n\nb\n", "c"));
    }

    @Test
    public void historyOfThisBootIsKept() {
        assertEquals("b\na", Hider.currentHistory("b\na", 5, 5));
    }

    @Test
    public void historyOfAnEarlierBootIsDropped() {
        assertEquals("", Hider.currentHistory("b\na", 4, 5));
        assertEquals("c", Hider.prepend(Hider.currentHistory("b\na", 4, 5), "c"));
    }

    // History lines, as MainActivity.showHistory splits them

    private static String[] fields(String line) {
        return line.split(" {2}");
    }

    @Test
    public void lineForABootWrite() {
        String[] f = fields(Hider.historyLine(T, 12_900,
                "android.intent.action.LOCKED_BOOT_COMPLETED", 0, "→1"));
        assertEquals(4, f.length);
        assertTrue(f[0], f[0].matches("\\d\\d:\\d\\d:\\d\\d"));
        assertEquals("+12.9 s", f[1]);
        assertEquals("LOCKED_BOOT_COMPLETED", f[2]);
        assertEquals("0→1", f[3]);
    }

    @Test
    public void lineWithFlag() {
        String[] f = fields(Hider.historyLine(T, 60_000, "trigger", 0, "→1  paused"));
        assertEquals(5, f.length);
        assertEquals("0→1", f[3]);
        assertEquals("paused", f[4]);
    }

    @Test
    public void lineWhileOff() {
        String[] f = fields(Hider.historyLine(T, 1_000, "trigger", 0, "  off"));
        assertEquals(5, f.length);
        assertEquals("0", f[3]);
        assertEquals("off", f[4]);
    }

    @Test
    public void lineUsesADecimalPointInAnyLocale() {
        Locale saved = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("+12.9 s", fields(Hider.historyLine(T, 12_900, "on", 0, "→1"))[1]);
        } finally {
            Locale.setDefault(saved);
        }
    }
}
