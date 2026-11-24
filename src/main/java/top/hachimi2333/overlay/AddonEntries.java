package top.hachimi2333.overlay;

import net.neoforged.fml.ModList;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.function.Function;
import java.util.function.Supplier;

public final class AddonEntries {
    private static final int LIMIT = 512;
    private static final int MAX_LABEL = 80;
    private static final int MAX_SOURCE_LOOKUPS = 128;
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final Source UNKNOWN = new Source("", "");

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private Map<String, Source> sources;
    private final ClassValue<Source> directSources = new ClassValue<>() {
        @Override
        protected Source computeValue(Class<?> caller) {
            Source source = sourceOf(caller);
            return source == null ? UNKNOWN : source;
        }
    };

    public List<Entry> entries() {
        return List.copyOf(entries.values());
    }

    private Source findSource(Class<?> caller) {
        if (sources == null) {
            sources = new HashMap<>();
            for (var file : ModList.get().getModFiles()) {
                var mods = file.getMods();
                if (mods.size() == 1) {
                    var mod = mods.getFirst();
                    sources.put(file.moduleName(), new Source(mod.getModId(), mod.getDisplayName()));
                }
            }
        }
        Source direct = directSources.get(caller);
        if (direct != UNKNOWN) return direct;
        return WALKER.walk(frames -> frames.skip(1)
                .takeWhile(frame -> !frame.getClassName().equals("net.neoforged.bus.EventBus"))
                .limit(24)
                .map(frame -> sourceOf(frame.getDeclaringClass()))
                .filter(source -> source != null)
                .findFirst().orElse(UNKNOWN));
    }

    private Source sourceOf(Class<?> caller) {
        Source source = sources.get(caller.getModule().getName());
        return source == null || source.id().equals("f3ather")
                || source.id().equals("minecraft") || source.id().equals("neoforge") ? null : source;
    }

    public Column column(boolean left, List<Row> rows) {
        return new Column(left, rows, this::findSource);
    }

    private Entry discover(boolean left, Map<String, Integer> occurrences, Source source, String text) {
        String side = left ? "left:" : "right:";
        String label = findLabel(text);
        String name = label == null ? "" : label;
        String prefix = "addon:" + side;
        String owner = source.id().isEmpty() ? "unknown" : source.id();
        String slot = prefix + owner + ":" + name;
        int occurrence = occurrences.merge(slot, 1, Integer::sum);
        String id = slot + ":" + occurrence;
        if (!entries.containsKey(id) && entries.size() >= LIMIT) {
            id = prefix + "overflow";
            source = UNKNOWN;
            name = "";
        }
        Entry previous = entries.get(id);
        if (previous != null) return previous;
        String example = stripFormatting(text);
        if (example.length() > 150) example = example.substring(0, 150);
        Entry entry = new Entry(id, source, name, example);
        entries.put(id, entry);
        return entry;
    }

    public boolean collect(Column column, Map<String, List<String>> grouped, List<String> appended) {
        Map<String, Integer> occurrences = new HashMap<>();
        boolean detected = false;
        for (String text : column) {
            if (text == null) continue;
            Row base = column.original.get(text);
            if (base != null) {
                if (base.id() != null && !text.isEmpty()) {
                    grouped.computeIfAbsent(base.id(), ignored -> new ArrayList<>()).add(text);
                }
                continue;
            }
            appended.add(text);
            if (text.isBlank()) continue;
            detected = true;
            Entry entry = discover(column.left, occurrences, column.added.getOrDefault(text, UNKNOWN), text);
            grouped.computeIfAbsent(entry.id(), ignored -> new ArrayList<>()).add(text);
        }
        return detected;
    }

    private static String findLabel(String line) {
        String plain = stripFormatting(line);
        int limit = Math.min(plain.length(), MAX_LABEL + 1);
        for (int index = 0; index < limit; index++) {
            char c = plain.charAt(index);
            if (c == '：' || ((c == ':' || c == '=') && index + 1 < plain.length()
                    && Character.isWhitespace(plain.charAt(index + 1)))) {
                String label = plain.substring(0, index).strip();
                return !label.isEmpty() && Character.isLetter(label.codePointAt(0)) ? label : null;
            }
        }
        return null;
    }

    public static String stripFormatting(String text) {
        int start = text.indexOf('§');
        if (start < 0) return text;
        var plain = new StringBuilder(text.length()).append(text, 0, start);
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '§' && i + 1 < text.length()) i++;
            else plain.append(ch);
        }
        return plain.toString();
    }

    public record Row(String id, String text) {}
    public record Source(String id, String name) {}
    public record Entry(String id, Source source, String name, String sample) {
        public String title() { return name.isEmpty() ? sample : name; }
    }

    public static final class Column extends AbstractList<String> implements RandomAccess {
        private final boolean left;
        private final List<String> lines;
        private final Map<String, Row> original = new IdentityHashMap<>();
        private final Map<String, Source> added = new IdentityHashMap<>();
        private final Function<Class<?>, Source> source;
        private int lookups;

        Column(boolean left, List<Row> rows, Function<Class<?>, Source> source) {
            this.left = left;
            this.source = source;
            lines = new ArrayList<>(rows.size());
            for (Row row : rows) {
                String value = row.text() == null ? null : new String(row.text());
                if (value != null) original.put(value, row);
                lines.add(value);
            }
        }

        @Override
        public String get(int index) { return lines.get(index); }

        @Override
        public int size() { return lines.size(); }

        @Override
        public String set(int index, String value) {
            String old = lines.set(index, value);
            if (value != null && !original.containsKey(value) && !added.containsKey(value)) {
                Row row = original.get(old);
                if (row != null) original.put(value, row);
                else if (source != null) {
                    Source owner = added.get(old);
                    added.put(value, owner == null ? resolve(WALKER.getCallerClass()) : owner);
                }
            }
            return old;
        }

        @Override
        public void add(int index, String value) {
            insert(index, value, source == null ? null : WALKER.getCallerClass());
        }

        @Override
        public boolean add(String value) {
            insert(lines.size(), value, source == null ? null : WALKER.getCallerClass());
            return true;
        }

        private void insert(int index, String value, Class<?> caller) {
            if ((added.containsKey(value) || original.containsKey(value)) && containsIdentity(value)) {
                value = new String(value);
            }
            lines.add(index, value);
            modCount++;
            if (caller != null) record(value, () -> resolve(caller));
        }

        @Override
        public boolean addAll(Collection<? extends String> values) {
            return insertAll(size(), values, source == null ? null : WALKER.getCallerClass());
        }

        @Override
        public boolean addAll(int index, Collection<? extends String> values) {
            return insertAll(index, values, source == null ? null : WALKER.getCallerClass());
        }

        private boolean insertAll(int index, Collection<? extends String> values, Class<?> caller) {
            if (caller == null) {
                if (!lines.addAll(index, values)) return false;
                modCount++;
                return true;
            }
            var copy = new ArrayList<String>(values);
            if (copy.isEmpty()) return false;
            Source owner = resolve(caller);
            for (int i = 0; i < copy.size(); i++) {
                String value = copy.get(i);
                if ((added.containsKey(value) || original.containsKey(value)) && containsIdentity(value)) {
                    value = new String(value);
                    copy.set(i, value);
                }
                record(value, () -> owner);
            }
            lines.addAll(index, copy);
            modCount++;
            return true;
        }

        @Override
        public String remove(int index) {
            String old = lines.remove(index);
            modCount++;
            return old;
        }

        @Override
        protected void removeRange(int from, int to) {
            if (from == to) return;
            lines.subList(from, to).clear();
            modCount++;
        }

        private boolean containsIdentity(String value) {
            for (String line : lines) if (line == value) return true;
            return false;
        }

        private void record(String value, Supplier<Source> owner) {
            if (value != null && !original.containsKey(value) && !added.containsKey(value)) {
                added.put(value, owner.get());
            }
        }

        private Source resolve(Class<?> caller) {
            return lookups++ < MAX_SOURCE_LOOKUPS ? source.apply(caller) : UNKNOWN;
        }
    }
}
