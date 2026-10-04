package textmenu.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only console model for MCODE output.
 * It keeps every printed line instead of treating the result as a single
 * editable text field.
 */
public final class MCodeConsoleModel {

    private final List<String> lines = new ArrayList<>();
    private int scrollLine;

    public void clear() {
        lines.clear();
        scrollLine = 0;
    }

    public void setText(String output) {
        clear();
        append(output);
    }

    public void append(String output) {
        if (output == null || output.isEmpty()) return;

        String normalized = output.replace("\r\n", "\n").replace('\r', '\n');
        String[] split = normalized.split("\n", -1);

        for (int i = 0; i < split.length; i++) {
            String line = split[i];
            if (i == split.length - 1 && line.isEmpty()) break;
            lines.add(line);
        }

        if (lines.isEmpty()) lines.add("");
        scrollToBottom();
    }

    public void appendLine(String line) {
        lines.add(line == null ? "" : line);
        scrollToBottom();
    }

    public List<String> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public int lineCount() {
        return lines.size();
    }

    public String getLine(int index) {
        return lines.get(index);
    }

    public int getScrollLine() {
        return scrollLine;
    }

    public void scrollLines(int delta, int visibleRows) {
        int max = Math.max(0, lines.size() - Math.max(1, visibleRows));
        scrollLine = Math.max(0, Math.min(max, scrollLine + delta));
    }

    public void scrollToTop() {
        scrollLine = 0;
    }

    public void scrollToBottom() {
        scrollLine = Math.max(0, lines.size() - 1);
    }

    public List<String> getVisibleLines(int visibleRows) {
        int count = Math.max(1, visibleRows);
        int start = Math.max(0, Math.min(scrollLine, Math.max(0, lines.size() - count)));
        int end = Math.min(lines.size(), start + count);
        return List.copyOf(lines.subList(start, end));
    }
}
