package textmenu.editor;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Text editing model for the MCODE editor.
 * Does not depend on Minecraft UI classes, so CodeEditorWidget can delegate
 * editing operations to it without changing the interpreter.
 */
public final class MCodeEditorModel {

    public static final int INDENT_SIZE = 4;

    private final StringBuilder text = new StringBuilder();
    private int cursor;
    private Integer anchor;
    private int preferredColumn = -1;

    private final Deque<State> undo = new ArrayDeque<>();
    private final Deque<State> redo = new ArrayDeque<>();
    private boolean restoring;

    public String getText() {
        return text.toString();
    }

    public void setText(String value) {
        text.setLength(0);
        if (value != null) text.append(value.replace("\r", ""));
        cursor = Math.min(cursor, text.length());
        anchor = null;
        preferredColumn = -1;
        undo.clear();
        redo.clear();
    }

    public int getCursor() {
        return cursor;
    }

    public void setCursor(int index) {
        cursor = clamp(index);
        anchor = null;
        preferredColumn = -1;
    }

    public boolean hasSelection() {
        return anchor != null && anchor != cursor;
    }

    public int selectionStart() {
        return hasSelection() ? Math.min(anchor, cursor) : cursor;
    }

    public int selectionEnd() {
        return hasSelection() ? Math.max(anchor, cursor) : cursor;
    }

    public String selectedText() {
        return hasSelection() ? text.substring(selectionStart(), selectionEnd()) : "";
    }

    public void selectAll() {
        anchor = 0;
        cursor = text.length();
        preferredColumn = -1;
    }

    public void clearSelection() {
        anchor = null;
    }

    public String cut() {
        String selected = selectedText();
        if (!selected.isEmpty()) deleteSelection();
        return selected;
    }

    public String copy() {
        return selectedText();
    }

    public void paste(String value) {
        if (value == null || value.isEmpty()) return;
        replaceSelection(value.replace("\r\n", "\n").replace('\r', '\n'));
    }

    public void undo() {
        if (undo.isEmpty()) return;
        State current = state();
        State previous = undo.pop();
        redo.push(current);
        restore(previous);
    }

    public void redo() {
        if (redo.isEmpty()) return;
        State current = state();
        State next = redo.pop();
        undo.push(current);
        restore(next);
    }

    public void insertText(String value) {
        if (value == null || value.isEmpty()) return;
        pushUndo();
        deleteSelectionNoUndo();
        text.insert(cursor, value);
        cursor += value.length();
        anchor = null;
        preferredColumn = -1;
    }

    /**
     * Handles a normal character typed in the editor.
     * Returns true when the character was consumed.
     */
    public boolean typeCharacter(char ch) {
        if (ch == '\t') {
            insertTab();
            return true;
        }

        if (ch == '\n') {
            insertSmartNewline();
            return true;
        }

        if (isOpeningPair(ch)) {
            char closing = matchingClose(ch);

            if (hasSelection()) {
                pushUndo();
                String selected = selectedText();
                int start = selectionStart();
                int end = selectionEnd();
                text.replace(start, end, "" + ch + selected + closing);
                cursor = start + selected.length() + 2;
                anchor = null;
                preferredColumn = -1;
                return true;
            }

            pushUndo();
            text.insert(cursor, "" + ch + closing);
            cursor++;
            preferredColumn = -1;
            return true;
        }

        if (isClosingPair(ch) && !hasSelection() && cursor < text.length() && text.charAt(cursor) == ch) {
            cursor++;
            preferredColumn = -1;
            return true;
        }

        insertText(String.valueOf(ch));
        return true;
    }

    public void backspace() {
        if (hasSelection()) {
            deleteSelection();
            return;
        }

        if (cursor <= 0) return;

        if (cursor < text.length() && isOpeningPair(text.charAt(cursor - 1))
                && matchingClose(text.charAt(cursor - 1)) == text.charAt(cursor)) {
            pushUndo();
            text.delete(cursor - 1, cursor + 1);
            cursor--;
            preferredColumn = -1;
            return;
        }

        pushUndo();
        text.deleteCharAt(cursor - 1);
        cursor--;
        preferredColumn = -1;
    }

    public void delete() {
        if (hasSelection()) {
            deleteSelection();
            return;
        }

        if (cursor >= text.length()) return;

        if (isOpeningPair(text.charAt(cursor)) && cursor + 1 < text.length()
                && matchingClose(text.charAt(cursor)) == text.charAt(cursor + 1)) {
            pushUndo();
            text.delete(cursor, cursor + 2);
            preferredColumn = -1;
            return;
        }

        pushUndo();
        text.deleteCharAt(cursor);
        preferredColumn = -1;
    }

    public void insertTab() {
        int col = column();
        int count = INDENT_SIZE - (col % INDENT_SIZE);
        insertText(" ".repeat(count));
    }

    public void unindent() {
        int lineStart = lineStart(cursor);
        int count = 0;
        while (lineStart + count < text.length() && count < INDENT_SIZE && text.charAt(lineStart + count) == ' ') {
            count++;
        }
        if (count == 0) return;

        pushUndo();
        text.delete(lineStart, lineStart + count);
        if (cursor >= lineStart + count) cursor -= count;
        else cursor = lineStart;
        preferredColumn = -1;
    }

    public void moveLeft(boolean shift, boolean ctrl) {
        prepareSelection(shift);
        if (cursor == 0) return;
        cursor = ctrl ? previousWordBoundary(cursor) : cursor - 1;
        preferredColumn = -1;
        finishSelection(shift);
    }

    public void moveRight(boolean shift, boolean ctrl) {
        prepareSelection(shift);
        if (cursor == text.length()) return;
        cursor = ctrl ? nextWordBoundary(cursor) : cursor + 1;
        preferredColumn = -1;
        finishSelection(shift);
    }

    public void moveHome(boolean shift, boolean ctrl) {
        prepareSelection(shift);
        cursor = ctrl ? 0 : lineStart(cursor);
        preferredColumn = -1;
        finishSelection(shift);
    }

    public void moveEnd(boolean shift, boolean ctrl) {
        prepareSelection(shift);
        cursor = ctrl ? text.length() : lineEnd(cursor);
        preferredColumn = -1;
        finishSelection(shift);
    }

    public void moveUp(boolean shift) {
        moveVertical(-1, shift);
    }

    public void moveDown(boolean shift) {
        moveVertical(1, shift);
    }

    public void pageUp(int visibleRows, boolean shift) {
        moveVertical(-Math.max(1, visibleRows), shift);
    }

    public void pageDown(int visibleRows, boolean shift) {
        moveVertical(Math.max(1, visibleRows), shift);
    }

    private void moveVertical(int delta, boolean shift) {
        int line = lineNumber(cursor);
        int col = preferredColumn >= 0 ? preferredColumn : column();
        prepareSelection(shift);
        int target = Math.max(0, Math.min(line + delta, lineCount() - 1));
        cursor = indexFromLineColumn(target, col);
        preferredColumn = col;
        finishSelection(shift);
    }

    private void prepareSelection(boolean shift) {
        if (shift) {
            if (anchor == null) anchor = cursor;
        } else {
            anchor = null;
        }
    }

    private void finishSelection(boolean shift) {
        if (!shift) anchor = null;
    }

    private void deleteSelection() {
        pushUndo();
        deleteSelectionNoUndo();
        preferredColumn = -1;
    }

    private void replaceSelection(String value) {
        pushUndo();
        deleteSelectionNoUndo();
        text.insert(cursor, value);
        cursor += value.length();
        preferredColumn = -1;
    }

    private void deleteSelectionNoUndo() {
        if (!hasSelection()) return;
        int start = selectionStart();
        int end = selectionEnd();
        text.delete(start, end);
        cursor = start;
        anchor = null;
    }

    private void insertSmartNewline() {
        pushUndo();

        int start = lineStart(cursor);
        String prefix = text.substring(start, cursor);
        int spaces = 0;
        while (spaces < prefix.length() && prefix.charAt(spaces) == ' ') spaces++;

        StringBuilder indentation = new StringBuilder();
        indentation.append(" ".repeat(spaces));

        String trimmed = prefix.trim();
        if (trimmed.endsWith(":")) indentation.append(" ".repeat(INDENT_SIZE));

        deleteSelectionNoUndo();
        text.insert(cursor, "\n" + indentation);
        cursor += 1 + indentation.length();
        anchor = null;
        preferredColumn = -1;
    }

    private void pushUndo() {
        if (restoring) return;
        undo.push(state());
        while (undo.size() > 200) undo.removeLast();
        redo.clear();
    }

    private State state() {
        return new State(text.toString(), cursor, anchor);
    }

    private void restore(State state) {
        restoring = true;
        try {
            text.setLength(0);
            text.append(state.text());
            cursor = state.cursor();
            anchor = state.anchor();
            preferredColumn = -1;
        } finally {
            restoring = false;
        }
    }

    private int previousWordBoundary(int index) {
        int i = index;
        while (i > 0 && Character.isWhitespace(text.charAt(i - 1))) i--;
        while (i > 0 && isWordChar(text.charAt(i - 1))) i--;
        return i;
    }

    private int nextWordBoundary(int index) {
        int i = index;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        while (i < text.length() && isWordChar(text.charAt(i))) i++;
        return i;
    }

    private static boolean isWordChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
    }

    private static boolean isOpeningPair(char ch) {
        return ch == '"' || ch == '\'' || ch == '(' || ch == '[' || ch == '{';
    }

    private static boolean isClosingPair(char ch) {
        return ch == '"' || ch == '\'' || ch == ')' || ch == ']' || ch == '}';
    }

    private static char matchingClose(char ch) {
        return switch (ch) {
            case '"' -> '"';
            case '\'' -> '\'';
            case '(' -> ')';
            case '[' -> ']';
            case '{' -> '}';
            default -> throw new IllegalArgumentException("No pair for: " + ch);
        };
    }

    private int clamp(int index) {
        return Math.max(0, Math.min(index, text.length()));
    }

    private int lineStart(int index) {
        int i = clamp(index) - 1;
        while (i >= 0 && text.charAt(i) != '\n') i--;
        return i + 1;
    }

    private int lineEnd(int index) {
        int i = clamp(index);
        while (i < text.length() && text.charAt(i) != '\n') i++;
        return i;
    }

    private int lineNumber(int index) {
        int line = 0;
        for (int i = 0; i < clamp(index); i++) if (text.charAt(i) == '\n') line++;
        return line;
    }

    private int lineCount() {
        int count = 1;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') count++;
        return count;
    }

    private int column() {
        return cursor - lineStart(cursor);
    }

    private int indexFromLineColumn(int targetLine, int targetColumn) {
        int line = 0;
        int start = 0;
        for (int i = 0; i < text.length() && line < targetLine; i++) {
            if (text.charAt(i) == '\n') {
                line++;
                start = i + 1;
            }
        }
        int end = start;
        while (end < text.length() && text.charAt(end) != '\n') end++;
        return Math.min(start + Math.max(0, targetColumn), end);
    }

    private record State(String text, int cursor, Integer anchor) {}
}
