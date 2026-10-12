package textmenu.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class ConsoleBuffer {

    private static final int MAX_LINE_CHARS = 4000;

    private final int maxLines;
    private final ArrayDeque<String> lines = new ArrayDeque<>();
    private final StringBuilder partial = new StringBuilder();
    private volatile long revision;

    public ConsoleBuffer(int maxLines) {
        this.maxLines = maxLines;
    }

    public synchronized void append(String text) {
        if (text == null || text.isEmpty()) return;
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        int start = 0;
        while (true) {
            int newline = normalized.indexOf('\n', start);
            if (newline < 0) {
                partial.append(normalized, start, normalized.length());
                if (partial.length() > MAX_LINE_CHARS * 2) pushPartial();
                break;
            }
            partial.append(normalized, start, newline);
            pushPartial();
            start = newline + 1;
        }
        revision++;
    }

    public synchronized void set(String text) {
        lines.clear();
        partial.setLength(0);
        append(text);
        revision++;
    }

    public synchronized void clear() {
        lines.clear();
        partial.setLength(0);
        revision++;
    }

    public synchronized List<String> snapshot() {
        List<String> out = new ArrayList<>(lines.size() + 1);
        out.addAll(lines);
        if (partial.length() > 0) out.add(clip(partial.toString()));
        return out;
    }

    public long revision() {
        return revision;
    }

    private void pushPartial() {
        lines.addLast(clip(partial.toString()));
        partial.setLength(0);
        while (lines.size() > maxLines) lines.removeFirst();
    }

    private static String clip(String line) {
        return line.length() > MAX_LINE_CHARS ? line.substring(0, MAX_LINE_CHARS) + "..." : line;
    }
}
