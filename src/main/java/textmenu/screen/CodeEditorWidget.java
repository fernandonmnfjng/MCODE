package textmenu.screen;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import textmenu.editor.MCodeEditorKeyHandler;
import textmenu.editor.MCodeEditorModel;
import textmenu.interpreter.MCodeCompletion;
import textmenu.interpreter.MCodeSyntaxHighlighter;

import java.util.Collections;
import java.util.List;

/**
 * MCODE multiline editor.
 *
 * Important viewport rule:
 * document coordinates are never derived directly from screen coordinates.
 * Mouse -> viewport row -> document line -> document column.
 */
public class CodeEditorWidget extends ClickableWidget {

    private static final int BACKGROUND = 0xFF0D1117;
    private static final int BORDER = 0xFF30363D;
    private static final int GUTTER = 0xFF11161D;
    private static final int LINE_NUMBER = 0xFF6E7681;
    private static final int CURRENT_LINE = 0x141F6FEB;
    private static final int SELECTION = 0x804A78C2;
    private static final int CARET = 0xFFE6EDF3;

    private static final int PADDING = 6;
    private static final int LINE_HEIGHT = 10;
    private static final int GUTTER_PADDING = 10;
    private static final int SCROLL_LINES_PER_WHEEL = 3;

    private final TextRenderer textRenderer;
    private final MCodeEditorModel editorModel = new MCodeEditorModel();

    /** First document line currently visible. */
    private int scrollLine;

    /** Horizontal pixel offset of the document. */
    private double scrollX;

    private boolean dragging;
    private int dragAnchor = -1;

    // Autocompletion state
    private List<MCodeCompletion.Suggestion> completionList = Collections.emptyList();
    private int completionIndex = 0;
    private boolean completionPopupVisible = false;
    private int completionScrollOffset = 0;
    private static final int MAX_POPUP_ITEMS = 7;

    private boolean isCyclingCompletion = false;
    private int cyclingStart = -1;
    private int cyclingInsertedLength = 0;

    private int lastPopupX = 0;
    private int lastPopupY = 0;
    private int lastPopupWidth = 0;
    private int lastPopupHeight = 0;
    private int lastPopupItemHeight = 12;

    public CodeEditorWidget(
            int x,
            int y,
            int width,
            int height,
            Text message,
            TextRenderer textRenderer
    ) {
        super(x, y, width, height, message);
        this.textRenderer = textRenderer;
    }

    public CodeEditorWidget(
            int x,
            int y,
            int width,
            int height,
            TextRenderer textRenderer
    ) {
        this(x, y, width, height, Text.literal("MCODE"), textRenderer);
    }

    public String getText() {
        return editorModel.getText();
    }

    public String getCode() {
        return getText();
    }

    public void setText(String text) {
        dismissCompletion();
        editorModel.setText(text == null ? "" : text);
        scrollLine = 0;
        scrollX = 0;
        clampScroll();
    }

    public void setCode(String code) {
        setText(code);
    }

    public MCodeEditorModel getEditorModel() {
        return editorModel;
    }

    public int getScrollLine() {
        return scrollLine;
    }

    public void scrollToLine(int line) {
        scrollLine = clampLine(line);
        ensureCursorVisible();
    }

    @Override
    protected void renderWidget(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta
    ) {
        clampScroll();

        int left = getX();
        int top = getY();
        int right = getX() + getWidth();
        int bottom = getY() + getHeight();

        context.fill(left, top, right, bottom, BACKGROUND);
        context.fill(left, top, right, top + 1, BORDER);
        context.fill(left, bottom - 1, right, bottom, BORDER);
        context.fill(left, top, left + 1, bottom, BORDER);
        context.fill(right - 1, top, right, bottom, BORDER);

        String source = editorModel.getText();
        String[] lines = source.split("\\n", -1);
        int lineCount = Math.max(1, lines.length);

        int visibleRows = getVisibleRows();
        int gutterWidth = getGutterWidth(lineCount);

        int codeLeft = left + gutterWidth;
        int codeTop = top + PADDING;
        int codeRight = right - PADDING;
        int codeBottom = bottom - PADDING;

        int firstLine = Math.min(scrollLine, lineCount - 1);
        int lastLine = Math.min(
                lineCount,
                firstLine + visibleRows
        );

        context.fill(
                left + 1,
                top + 1,
                codeLeft,
                bottom - 1,
                GUTTER
        );

        int currentLine = cursorLine();
        if (currentLine >= firstLine && currentLine < lastLine) {
            int row = currentLine - firstLine;
            int lineY = codeTop + row * LINE_HEIGHT;
            context.fill(
                    codeLeft,
                    lineY - 1,
                    codeRight,
                    lineY + LINE_HEIGHT,
                    CURRENT_LINE
            );
        }

        context.enableScissor(
                left + 1,
                top + 1,
                right - 1,
                bottom - 1
        );

        try {
            for (int lineIndex = firstLine; lineIndex < lastLine; lineIndex++) {
                int row = lineIndex - firstLine;
                int lineY = codeTop + row * LINE_HEIGHT;
                String line = lines[lineIndex];

                drawLineNumber(
                        context,
                        lineIndex + 1,
                        left + 4,
                        lineY,
                        gutterWidth - 8
                );

                drawSelection(
                        context,
                        line,
                        lineIndex,
                        codeLeft,
                        lineY
                );

                drawSyntaxLine(
                        context,
                        line,
                        codeLeft,
                        lineY
                );
            }

            drawCaret(
                    context,
                    lines,
                    firstLine,
                    codeLeft,
                    codeTop,
                    codeBottom
            );
        } finally {
            context.disableScissor();
        }

        if (hasVerticalOverflow(lineCount, visibleRows)) {
            drawScrollbar(
                    context,
                    lineCount,
                    visibleRows,
                    top,
                    bottom,
                    right
            );
        }

        if (completionPopupVisible && !completionList.isEmpty()) {
            drawCompletionPopup(context, mouseX, mouseY);
        }
    }

    private void drawLineNumber(
            DrawContext context,
            int number,
            int x,
            int y,
            int availableWidth
    ) {
        String value = String.valueOf(number);
        int textWidth = textRenderer.getWidth(value);
        int drawX = x + Math.max(0, availableWidth - textWidth);

        context.drawText(
                textRenderer,
                value,
                drawX,
                y,
                LINE_NUMBER,
                false
        );
    }

    private void drawSyntaxLine(
            DrawContext context,
            String line,
            int x,
            int y
    ) {
        int drawX = (int) Math.round(x - scrollX);

        for (MCodeSyntaxHighlighter.Token token : MCodeSyntaxHighlighter.tokenize(line)) {
            if (token.text().isEmpty()) {
                continue;
            }

            context.drawText(
                    textRenderer,
                    token.text(),
                    drawX,
                    y,
                    MCodeSyntaxHighlighter.getColor(token.type()),
                    false
            );

            drawX += textRenderer.getWidth(token.text());
        }
    }

    private void drawSelection(
            DrawContext context,
            String line,
            int lineIndex,
            int codeLeft,
            int lineY
    ) {
        if (!editorModel.hasSelection()) {
            return;
        }

        int lineStart = lineStartIndex(lineIndex, editorModel.getText());
        int lineEnd = lineStart + line.length();

        int selectionStart = editorModel.selectionStart();
        int selectionEnd = editorModel.selectionEnd();

        int start = Math.max(selectionStart, lineStart);
        int end = Math.min(selectionEnd, lineEnd);

        if (start > end || start == end) {
            return;
        }

        int startColumn = start - lineStart;
        int endColumn = end - lineStart;

        String startText = line.substring(0, startColumn);
        String selectedText = line.substring(startColumn, endColumn);

        int x1 = (int) Math.round(
                codeLeft
                        - scrollX
                        + textRenderer.getWidth(startText)
        );

        int width = textRenderer.getWidth(selectedText);

        if (selectedText.isEmpty() && startColumn == line.length()) {
            width = Math.max(2, textRenderer.getWidth(" "));
        }

        context.fill(
                x1,
                lineY - 1,
                x1 + width,
                lineY + LINE_HEIGHT,
                SELECTION
        );
    }

    private void drawCaret(
            DrawContext context,
            String[] lines,
            int firstLine,
            int codeLeft,
            int codeTop,
            int codeBottom
    ) {
        if (!isFocused()) {
            return;
        }

        long time = System.currentTimeMillis();
        if ((time / 500L) % 2L != 0L) {
            return;
        }

        int cursorLine = cursorLine();
        int cursorColumn = cursorColumn();

        if (cursorLine < firstLine) {
            return;
        }

        int row = cursorLine - firstLine;
        int y = codeTop + row * LINE_HEIGHT;

        if (y < codeTop - LINE_HEIGHT || y > codeBottom) {
            return;
        }

        String line = cursorLine < lines.length
                ? lines[cursorLine]
                : "";

        int column = Math.min(cursorColumn, line.length());

        int x = (int) Math.round(
                codeLeft
                        - scrollX
                        + textRenderer.getWidth(
                                line.substring(0, column)
                        )
        );

        context.fill(
                x,
                y - 1,
                x + 1,
                y + LINE_HEIGHT,
                CARET
        );
    }

    private void drawScrollbar(
            DrawContext context,
            int lineCount,
            int visibleRows,
            int top,
            int bottom,
            int right
    ) {
        int trackTop = top + 2;
        int trackBottom = bottom - 2;
        int trackHeight = Math.max(1, trackBottom - trackTop);

        int thumbHeight = Math.max(
                12,
                (int) ((double) trackHeight * visibleRows / lineCount)
        );

        int maxScroll = Math.max(1, lineCount - visibleRows);
        int maxThumbTravel = Math.max(0, trackHeight - thumbHeight);

        int thumbY = trackTop + (int) (
                (double) scrollLine / maxScroll * maxThumbTravel
        );

        context.fill(
                right - 4,
                trackTop,
                right - 2,
                trackBottom,
                0x602F3640
        );

        context.fill(
                right - 5,
                thumbY,
                right - 1,
                thumbY + thumbHeight,
                0xA6AAB2BC
        );
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        if (completionPopupVisible && isMouseOverPopup(mouseX, mouseY)) {
            int clickedRow = (int) ((mouseY - (lastPopupY + 2)) / (double) lastPopupItemHeight);
            int clickedIndex = completionScrollOffset + clickedRow;
            if (clickedIndex >= 0 && clickedIndex < completionList.size()) {
                completionIndex = clickedIndex;
                applyCurrentCompletion();
            }
            dismissCompletion();
            return true;
        }

        if (completionPopupVisible) {
            dismissCompletion();
        }

        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }

        setFocused(true);
        dragging = true;

        int index = documentIndexFromMouse(mouseX, mouseY);
        editorModel.setCursor(index);
        dragAnchor = index;
        ensureCursorVisible();

        return true;
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double deltaX,
            double deltaY
    ) {
        if (!dragging || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        int codeTop = getY() + PADDING;
        int codeBottom = getY() + getHeight() - PADDING;

        if (mouseY < codeTop) {
            scrollLine--;
            clampScroll();
        } else if (mouseY > codeBottom) {
            scrollLine++;
            clampScroll();
        }

        int target = documentIndexFromMouse(mouseX, mouseY);
        selectFromDragAnchor(target);
        ensureCursorVisible();
        return true;
    }

    @Override
    public boolean mouseReleased(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = false;
            dragAnchor = -1;
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount
    ) {
        if (completionPopupVisible && isMouseOverPopup(mouseX, mouseY)) {
            if (verticalAmount != 0.0) {
                int visibleCount = Math.min(completionList.size(), MAX_POPUP_ITEMS);
                int maxScroll = Math.max(0, completionList.size() - visibleCount);
                completionScrollOffset += verticalAmount > 0 ? -1 : 1;
                completionScrollOffset = Math.max(0, Math.min(completionScrollOffset, maxScroll));
                return true;
            }
        }

        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }

        if (horizontalAmount != 0.0) {
            scrollX = Math.max(
                    0,
                    scrollX - horizontalAmount * 24.0
            );
        }

        if (verticalAmount != 0.0) {
            scrollLine += verticalAmount > 0
                    ? -SCROLL_LINES_PER_WHEEL
                    : SCROLL_LINES_PER_WHEEL;

            clampScroll();
        }

        return true;
    }

    @Override
    public boolean keyPressed(
            int keyCode,
            int scanCode,
            int modifiers
    ) {
        int visibleRows = getVisibleRows();

        // 1. Completion popup navigation & interaction
        if (completionPopupVisible) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                dismissCompletion();
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                if (!completionList.isEmpty()) {
                    completionIndex = (completionIndex + 1) % completionList.size();
                    if (isCyclingCompletion) {
                        applyCurrentCompletion();
                    }
                }
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_UP) {
                if (!completionList.isEmpty()) {
                    completionIndex = (completionIndex - 1 + completionList.size()) % completionList.size();
                    if (isCyclingCompletion) {
                        applyCurrentCompletion();
                    }
                }
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (!isCyclingCompletion) {
                    applyCurrentCompletion();
                }
                dismissCompletion();
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_TAB) {
                cycleCompletion();
                return true;
            }
        }

        // 2. Ctrl + Space: trigger completion manually
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && keyCode == GLFW.GLFW_KEY_SPACE) {
            updateCompletionPopup(true);
            return true;
        }

        // 3. Tab when popup is NOT visible: if cursor is at an identifier/dot and no selection, trigger completion & cycle
        if (keyCode == GLFW.GLFW_KEY_TAB && (modifiers & GLFW.GLFW_MOD_SHIFT) == 0 && !editorModel.hasSelection()) {
            String source = editorModel.getText();
            int cursor = editorModel.getCursor();
            int prefixLen = MCodeCompletion.getPrefixLength(source, cursor);
            boolean atDot = cursor > 0 && source.charAt(cursor - 1) == '.';
            if (prefixLen > 0 || atDot) {
                List<MCodeCompletion.Suggestion> results = MCodeCompletion.complete(source, cursor);
                if (!results.isEmpty()) {
                    completionList = results;
                    completionIndex = 0;
                    completionScrollOffset = 0;
                    completionPopupVisible = true;
                    cycleCompletion();
                    return true;
                }
            }
        }

        // If cycling was active and user pressed any other key, finalize cycling
        if (isCyclingCompletion && keyCode != GLFW.GLFW_KEY_LEFT_SHIFT && keyCode != GLFW.GLFW_KEY_RIGHT_SHIFT) {
            isCyclingCompletion = false;
            cyclingStart = -1;
            cyclingInsertedLength = 0;
        }

        if (MCodeEditorKeyHandler.handle(
                editorModel,
                keyCode,
                modifiers,
                MinecraftClient.getInstance(),
                visibleRows
        )) {
            if (ctrl && keyCode == GLFW.GLFW_KEY_V) normalizeBooleanLiterals();
            ensureCursorVisible();
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                updateCompletionPopup(false);
            } else if (completionPopupVisible) {
                dismissCompletion();
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            editorModel.typeCharacter('\n');
            ensureCursorVisible();
            dismissCompletion();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!isFocused()) {
            return false;
        }

        if (Character.isISOControl(chr)) {
            return false;
        }

        if (isCyclingCompletion) {
            isCyclingCompletion = false;
            cyclingStart = -1;
            cyclingInsertedLength = 0;
        }

        editorModel.typeCharacter(chr);
        normalizeBooleanAtCursor();
        ensureCursorVisible();

        updateCompletionPopup(false);
        return true;
    }

    private void normalizeBooleanAtCursor() {
        String source = editorModel.getText();
        int cursor = editorModel.getCursor();
        if (cursor <= 0 || cursor > source.length()) return;
        int lineStart = source.lastIndexOf('\n', cursor - 1) + 1;
        int lineEnd = source.indexOf('\n', cursor);
        if (lineEnd < 0) lineEnd = source.length();
        String line = source.substring(lineStart, lineEnd);
        if (lineStart == 0 && line.trim().matches("(?i)^SetPATH\\s+.*$")) return;
        int localCursor = cursor - lineStart;
        for (MCodeSyntaxHighlighter.Token token : MCodeSyntaxHighlighter.tokenize(line)) {
            if (token.end() != localCursor || token.type() != MCodeSyntaxHighlighter.TokenType.BOOLEAN) continue;
            String canonical = token.text().equalsIgnoreCase("true") ? "True" : "False";
            if (!token.text().equals(canonical)) editorModel.replaceRange(lineStart + token.start(), lineStart + token.end(), canonical);
            return;
        }
    }

    private void normalizeBooleanLiterals() {
        String source = editorModel.getText();
        String normalized = MCodeSyntaxHighlighter.normalizeBooleanLiterals(source);
        if (!source.equals(normalized)) {
            int cursor = editorModel.getCursor();
            editorModel.replaceRange(0, source.length(), normalized);
            editorModel.setCursor(Math.min(cursor, normalized.length()));
        }
    }

    private boolean isMouseOverPopup(double mouseX, double mouseY) {
        return mouseX >= lastPopupX && mouseX <= lastPopupX + lastPopupWidth
                && mouseY >= lastPopupY && mouseY <= lastPopupY + lastPopupHeight;
    }

    private void updateCompletionPopup(boolean force) {
        String source = editorModel.getText();
        int cursor = editorModel.getCursor();

        if (!force) {
            int prefixLen = MCodeCompletion.getPrefixLength(source, cursor);
            boolean canTrigger = MCodeCompletion.canTriggerAt(source, cursor);
            if (!canTrigger || (prefixLen == 0 && (cursor <= 0 || source.charAt(cursor - 1) != '.'))) {
                dismissCompletion();
                return;
            }
        }

        try {
            List<MCodeCompletion.Suggestion> results = MCodeCompletion.complete(source, cursor);
            if (results == null || results.isEmpty()) {
                dismissCompletion();
            } else {
                completionList = results;
                completionIndex = 0;
                completionScrollOffset = 0;
                completionPopupVisible = true;
            }
        } catch (Throwable t) {
            dismissCompletion();
        }
    }

    private void cycleCompletion() {
        if (completionList.isEmpty()) return;
        if (!isCyclingCompletion) {
            int prefixLen = MCodeCompletion.getPrefixLength(editorModel.getText(), editorModel.getCursor());
            cyclingStart = editorModel.getCursor() - prefixLen;
            completionIndex = 0;
            MCodeCompletion.Suggestion s = completionList.get(0);
            editorModel.replaceRange(cyclingStart, editorModel.getCursor(), s.insertText());
            cyclingInsertedLength = s.insertText().length();
            isCyclingCompletion = true;
        } else {
            completionIndex = (completionIndex + 1) % completionList.size();
            MCodeCompletion.Suggestion s = completionList.get(completionIndex);
            editorModel.replaceRange(cyclingStart, cyclingStart + cyclingInsertedLength, s.insertText());
            cyclingInsertedLength = s.insertText().length();
        }
        ensureCursorVisible();
    }

    private void applyCurrentCompletion() {
        if (completionList.isEmpty() || completionIndex < 0 || completionIndex >= completionList.size()) {
            return;
        }
        MCodeCompletion.Suggestion s = completionList.get(completionIndex);
        if (isCyclingCompletion) {
            editorModel.replaceRange(cyclingStart, cyclingStart + cyclingInsertedLength, s.insertText());
            cyclingInsertedLength = s.insertText().length();
        } else {
            int prefixLen = MCodeCompletion.getPrefixLength(editorModel.getText(), editorModel.getCursor());
            int start = editorModel.getCursor() - prefixLen;
            editorModel.replaceRange(start, editorModel.getCursor(), s.insertText());
            cyclingStart = start;
            cyclingInsertedLength = s.insertText().length();
        }
        ensureCursorVisible();
    }

    private void dismissCompletion() {
        completionPopupVisible = false;
        completionList = Collections.emptyList();
        completionIndex = 0;
        completionScrollOffset = 0;
        isCyclingCompletion = false;
        cyclingStart = -1;
        cyclingInsertedLength = 0;
    }

    private void drawCompletionPopup(DrawContext context, int mouseX, int mouseY) {
        String source = editorModel.getText();
        String[] lines = source.split("\\n", -1);
        int lineCount = Math.max(1, lines.length);

        int cursorLine = cursorLine();
        int cursorColumn = cursorColumn();
        int firstLine = Math.min(scrollLine, lineCount - 1);
        int row = cursorLine - firstLine;

        int gutterWidth = getGutterWidth(lineCount);
        int codeLeft = getX() + gutterWidth;
        int codeTop = getY() + PADDING;

        String line = cursorLine < lines.length ? lines[cursorLine] : "";
        int col = Math.min(cursorColumn, line.length());
        int caretX = (int) Math.round(codeLeft - scrollX + textRenderer.getWidth(line.substring(0, col)));
        int caretY = codeTop + row * LINE_HEIGHT;

        int itemHeight = 12;
        int totalItems = completionList.size();
        int visibleCount = Math.min(totalItems, MAX_POPUP_ITEMS);
        int popupHeight = visibleCount * itemHeight + 4;

        int maxTextWidth = 90;
        for (int i = 0; i < totalItems; i++) {
            MCodeCompletion.Suggestion s = completionList.get(i);
            int w = textRenderer.getWidth(s.insertText()) + (s.detail().isEmpty() ? 0 : textRenderer.getWidth(s.detail()) + 14);
            if (w > maxTextWidth) maxTextWidth = w;
        }
        int popupWidth = Math.min(250, maxTextWidth + 18);

        int popupX = caretX;
        if (popupX + popupWidth > getX() + getWidth() - 4) {
            popupX = getX() + getWidth() - popupWidth - 4;
        }
        if (popupX < getX() + 4) {
            popupX = getX() + 4;
        }

        int popupY = caretY + LINE_HEIGHT + 2;
        if (popupY + popupHeight > getY() + getHeight() - 4) {
            popupY = caretY - popupHeight - 2;
        }
        if (popupY < getY() + 2) {
            popupY = getY() + 2;
        }

        lastPopupX = popupX;
        lastPopupY = popupY;
        lastPopupWidth = popupWidth;
        lastPopupHeight = popupHeight;
        lastPopupItemHeight = itemHeight;

        // Background
        context.fill(popupX, popupY, popupX + popupWidth, popupY + popupHeight, 0xF5161B22);
        // Border
        context.fill(popupX, popupY, popupX + popupWidth, popupY + 1, 0xFF388BFD);
        context.fill(popupX, popupY + popupHeight - 1, popupX + popupWidth, popupY + popupHeight, 0xFF388BFD);
        context.fill(popupX, popupY, popupX + 1, popupY + popupHeight, 0xFF388BFD);
        context.fill(popupX + popupWidth - 1, popupY, popupX + popupWidth, popupY + popupHeight, 0xFF388BFD);

        if (completionIndex < completionScrollOffset) {
            completionScrollOffset = completionIndex;
        } else if (completionIndex >= completionScrollOffset + visibleCount) {
            completionScrollOffset = completionIndex - visibleCount + 1;
        }
        completionScrollOffset = Math.max(0, Math.min(completionScrollOffset, Math.max(0, totalItems - visibleCount)));

        for (int i = 0; i < visibleCount; i++) {
            int itemIdx = completionScrollOffset + i;
            if (itemIdx >= totalItems) break;

            MCodeCompletion.Suggestion item = completionList.get(itemIdx);
            int itemY = popupY + 2 + i * itemHeight;
            boolean isSelected = (itemIdx == completionIndex);

            if (isSelected) {
                context.fill(popupX + 1, itemY, popupX + popupWidth - 1, itemY + itemHeight, 0xFF1F6FEB);
            } else if (mouseX >= popupX && mouseX < popupX + popupWidth && mouseY >= itemY && mouseY < itemY + itemHeight) {
                context.fill(popupX + 1, itemY, popupX + popupWidth - 1, itemY + itemHeight, 0x40388BFD);
            }

            int textColor = isSelected ? 0xFFFFFFFF : 0xFFE6EDF3;
            context.drawText(textRenderer, item.insertText(), popupX + 4, itemY + 2, textColor, false);

            if (!item.detail().isEmpty()) {
                int detailColor = isSelected ? 0xFFD0D7DE : getDetailColor(item.detail());
                int detailWidth = textRenderer.getWidth(item.detail());
                int detailX = popupX + popupWidth - detailWidth - 6;
                if (detailX > popupX + textRenderer.getWidth(item.insertText()) + 8) {
                    context.drawText(textRenderer, item.detail(), detailX, itemY + 2, detailColor, false);
                }
            }
        }

        if (totalItems > visibleCount) {
            int trackH = popupHeight - 4;
            int thumbH = Math.max(6, trackH * visibleCount / totalItems);
            int thumbY = popupY + 2 + (trackH - thumbH) * completionScrollOffset / (totalItems - visibleCount);
            context.fill(popupX + popupWidth - 3, thumbY, popupX + popupWidth - 1, thumbY + thumbH, 0xFF7D8590);
        }
    }

    private static int getDetailColor(String detail) {
        return switch (detail) {
            case "keyword" -> 0xFFFF7B72;
            case "built-in" -> 0xFF7EE787;
            case "type" -> 0xFFFFA657;
            case "module" -> 0xFFD2A8FF;
            case "variable" -> 0xFF79C0FF;
            case "member" -> 0xFF58A6FF;
            default -> 0xFF8B949E;
        };
    }

    private int documentIndexFromMouse(double mouseX, double mouseY) {
        String source = editorModel.getText();
        String[] lines = source.split("\\n", -1);
        int lineCount = Math.max(1, lines.length);

        int firstLine = Math.min(scrollLine, lineCount - 1);
        int codeTop = getY() + PADDING;
        int gutterWidth = getGutterWidth(lineCount);
        int codeLeft = getX() + gutterWidth;

        int row = (int) Math.floor(
                (mouseY - codeTop) / (double) LINE_HEIGHT
        );

        int documentLine = firstLine + row;
        documentLine = Math.max(
                0,
                Math.min(documentLine, lineCount - 1)
        );

        String line = lines[documentLine];

        double documentX =
                mouseX
                        - codeLeft
                        + scrollX;

        int column = columnFromPixel(
                line,
                documentX
        );

        return lineStartIndex(documentLine, source) + column;
    }

    private void selectFromDragAnchor(int target) {
        if (dragAnchor < 0) {
            editorModel.setCursor(target);
            return;
        }

        editorModel.setCursor(dragAnchor);

        int distance = Math.abs(target - dragAnchor);

        if (target >= dragAnchor) {
            for (int i = 0; i < distance; i++) {
                editorModel.moveRight(true, false);
            }
        } else {
            for (int i = 0; i < distance; i++) {
                editorModel.moveLeft(true, false);
            }
        }
    }

    private int columnFromPixel(String line, double pixel) {
        if (pixel <= 0) {
            return 0;
        }

        int x = 0;

        for (int i = 0; i < line.length(); i++) {
            int width = textRenderer.getWidth(
                    String.valueOf(line.charAt(i))
            );

            if (pixel < x + width / 2.0) {
                return i;
            }

            x += width;
        }

        return line.length();
    }

    private void ensureCursorVisible() {
        int visibleRows = getVisibleRows();
        int lineCount = Math.max(1, lineCount());

        int cursorLine = cursorLine();
        int cursorColumn = cursorColumn();
        String line = lineAt(cursorLine, editorModel.getText());

        if (cursorLine < scrollLine) {
            scrollLine = cursorLine;
        } else if (cursorLine >= scrollLine + visibleRows) {
            scrollLine = cursorLine - visibleRows + 1;
        }

        int gutterWidth = getGutterWidth(lineCount);
        int codeWidth = Math.max(
                1,
                getWidth() - gutterWidth - PADDING * 2
        );

        int cursorPixel = textRenderer.getWidth(
                line.substring(0, Math.min(cursorColumn, line.length()))
        );

        int horizontalViewport = codeWidth;

        if (cursorPixel < scrollX) {
            scrollX = cursorPixel;
        } else if (cursorPixel > scrollX + horizontalViewport - 4) {
            scrollX = cursorPixel - horizontalViewport + 4;
        }

        clampScroll();
    }

    private void clampScroll() {
        int lineCount = Math.max(1, lineCount());
        int visibleRows = getVisibleRows();

        int maxScrollLine = Math.max(
                0,
                lineCount - visibleRows
        );

        scrollLine = Math.max(
                0,
                Math.min(scrollLine, maxScrollLine)
        );

        int gutterWidth = getGutterWidth(lineCount);
        int viewportWidth = Math.max(
                1,
                getWidth() - gutterWidth - PADDING * 2
        );

        String longestLine = longestLine(editorModel.getText());
        int contentWidth = textRenderer.getWidth(longestLine);
        int maxScrollX = Math.max(
                0,
                contentWidth - viewportWidth
        );

        scrollX = Math.max(
                0,
                Math.min(scrollX, maxScrollX)
        );
    }

    private int clampLine(int line) {
        int count = Math.max(1, lineCount());
        return Math.max(0, Math.min(line, count - 1));
    }

    private int getVisibleRows() {
        return Math.max(
                1,
                (getHeight() - PADDING * 2) / LINE_HEIGHT
        );
    }

    private int getGutterWidth(int lineCount) {
        int digits = String.valueOf(Math.max(1, lineCount)).length();
        int numberWidth = textRenderer.getWidth("0") * digits;
        return Math.max(
                30,
                numberWidth + GUTTER_PADDING * 2
        );
    }

    private boolean hasVerticalOverflow(
            int lineCount,
            int visibleRows
    ) {
        return lineCount > visibleRows;
    }

    private int lineCount() {
        String text = editorModel.getText();
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    private int cursorLine() {
        return lineNumber(editorModel.getCursor(), editorModel.getText());
    }

    private int cursorColumn() {
        String text = editorModel.getText();
        int start = lineStartIndex(
                cursorLine(),
                text
        );
        return editorModel.getCursor() - start;
    }

    private int lineNumber(int index, String text) {
        int clamped = Math.max(0, Math.min(index, text.length()));
        int line = 0;

        for (int i = 0; i < clamped; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }

        return line;
    }

    private int lineStartIndex(int targetLine, String text) {
        if (targetLine <= 0) {
            return 0;
        }

        int line = 0;

        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                if (line == targetLine) {
                    return i + 1;
                }
            }
        }

        return text.length();
    }

    private String lineAt(int targetLine, String text) {
        int count = 0;
        int start = 0;

        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                if (count == targetLine) {
                    return text.substring(start, i);
                }
                count++;
                start = i + 1;
            }
        }

        return count == targetLine
                ? text.substring(start)
                : "";
    }

    private String longestLine(String text) {
        String longest = "";
        int start = 0;

        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                if (i - start > longest.length()) {
                    longest = text.substring(start, i);
                }
                start = i + 1;
            }
        }

        return longest;
    }

    public boolean isWithinBounds(double mouseX, double mouseY) {
        return isMouseOver(mouseX, mouseY);
    }

    @Override
    protected void appendClickableNarrations(
            NarrationMessageBuilder builder
    ) {
        builder.put(NarrationPart.TITLE, Text.literal("MCODE code editor"));
    }
}
