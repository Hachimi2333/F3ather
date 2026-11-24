package top.hachimi2333.overlay;

import top.hachimi2333.overlay.AddonEntries.Row;
import top.hachimi2333.overlay.AddonEntries.Source;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/** Records strings emitted by the original collectors, before inspecting the actual returned list. */
public final class RawDebugRows {
    private static final Source SODIUM = new Source("sodium", "Sodium");
    private final List<String> vanilla = new ArrayList<>();
    private boolean initialized;

    public void initial(Object[] values) {
        initialized = true;
        for (Object value : values) append(value);
    }

    public void append(Object value) {
        vanilla.add((String) value);
    }

    public void prepend(Object value) {
        vanilla.addFirst((String) value);
    }

    public void appendWithPrefix(List<String> lines, int start, String prefix) {
        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line != null && line.startsWith(prefix)) vanilla.add(line);
        }
    }

    public List<Row> resolve(List<String> actual, boolean left, boolean integrated, boolean demo, boolean sodium) {
        // An overwritten collector may replace the stock construction site entirely.
        if (!initialized) return VanillaEntry.label(actual, left, integrated, demo);
        var baseline = VanillaEntry.label(vanilla, left, integrated, demo);
        // Match occurrences by identity so an inserted string with the same text does not replace a stock row.
        var first = new IdentityHashMap<String, Integer>();
        int[] next = new int[baseline.size()];
        for (int i = baseline.size() - 1; i >= 0; i--) {
            Integer later = first.put(baseline.get(i).text(), i);
            next[i] = later == null ? -1 : later;
        }

        var rows = new ArrayList<Row>(actual.size());
        boolean sodiumRenderer = false;
        for (String text : actual) {
            Integer index = first.get(text);
            if (index != null) {
                if (next[index] == -1) first.remove(text);
                else first.put(text, next[index]);
                rows.add(baseline.get(index));
                if (text != null && !text.isBlank()) sodiumRenderer = false;
            } else if (text == null || text.isBlank()) {
                rows.add(new Row(null, text));
            } else {
                Source source = AddonEntries.UNKNOWN;
                if (sodium) {
                    String plain = AddonEntries.stripFormatting(text);
                    if (left && plain.startsWith("p50=") && plain.contains(" p98=")
                            && plain.contains(" p99.5=") && plain.endsWith(" fps")) {
                        source = SODIUM;
                    } else if (!left) {
                        if (plain.startsWith("Sodium Renderer (")) {
                            sodiumRenderer = true;
                            source = SODIUM;
                        } else if (plain.startsWith("Off-Heap: +") || (sodiumRenderer && sodiumRendererLine(plain))) {
                            source = SODIUM;
                        }
                    }
                }
                rows.add(new Row(null, text, source));
            }
        }
        return List.copyOf(rows);
    }

    private static boolean sodiumRendererLine(String line) {
        return line.startsWith("Pools: Geometry ") || line.startsWith("Transfer Queue: ")
                || line.startsWith("Chunk Builder: ") || line.startsWith("Tasks: ")
                || line.startsWith("TS (") || line.equals("TS OFF") || line.startsWith("N=")
                || line.startsWith("Duration: Mesh ") || line.startsWith("Size: ");
    }
}
