package textmenu.screen;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ScrollableWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import textmenu.interpreter.MCodeSyntaxHighlighter;

public class CodeEditorWidget extends ScrollableWidget {

    private final TextRenderer textRenderer;
    private String code = "";
    private int cursorPos = 0;
    private int scrollOffset = 0;
    private long lastInteractionTime = System.currentTimeMillis();
    private int lastRenderedCursorPos = -1;
    private static final int LINE_HEIGHT = 12;
    private static final int TAB_SIZE = 4;
    private static final int PADDING = 4;
    private static final int LINE_NUMBER_WIDTH = 35;

    public CodeEditorWidget(TextRenderer textRenderer, int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
        this.textRenderer = textRenderer;
    }

    public String getCode() {
        return this.code;
    }

    public void setCode(String code) {
        this.code = code;
        this.cursorPos = code.length();
    }

    @Override
    protected int getContentsHeight() {
        int lines = countLines();
        return lines * LINE_HEIGHT + PADDING * 2;
    }

    @Override
    protected double getDeltaYPerScroll() {
        return LINE_HEIGHT;
    }

    @Override
    protected void renderContents(DrawContext context, int mouseX, int mouseY, float delta) {
        String[] lines = this.code.split("\n", -1);
        int startLine = (int) (this.scrollOffset / LINE_HEIGHT);
        int visibleLines = (this.height - PADDING * 2) / LINE_HEIGHT;

        for (int i = 0; i <= visibleLines && startLine + i < lines.length; i++) {
            int lineIndex = startLine + i;
            int y = this.getY() + PADDING + i * LINE_HEIGHT - (int) this.scrollOffset;

            context.drawTextWithShadow(
                    this.textRenderer,
                    Text.literal(String.format("%3d |", lineIndex + 1)),
                    this.getX() + PADDING,
                    y,
                    0x888888
            );

            if (!lines[lineIndex].isEmpty()) {
                MCodeSyntaxHighlighter.drawLine(
                        context,
                        this.textRenderer,
                        lines[lineIndex],
                        this.getX() + PADDING + LINE_NUMBER_WIDTH,
                        y
                );
            }
        }

        // Cursor parpadeante y trackeo de escritura
        if (this.cursorPos != this.lastRenderedCursorPos) {
            this.lastInteractionTime = System.currentTimeMillis();
            this.lastRenderedCursorPos = this.cursorPos;
        }

        boolean showCursor = true;
        if (System.currentTimeMillis() - this.lastInteractionTime > 500) {
            showCursor = (System.currentTimeMillis() / 500) % 2 == 0;
        }

        if (this.isFocused() && showCursor) {
            int[] cursor = getCursorPosition();
            if (cursor != null) {
                int cx = this.getX() + PADDING + LINE_NUMBER_WIDTH + this.textRenderer.getWidth(
                        lines[cursor[0]].substring(0, Math.min(cursor[1], lines[cursor[0]].length()))
                );
                int cy = this.getY() + PADDING + cursor[0] * LINE_HEIGHT - (int) this.scrollOffset;
                // Color blanco opaco (0xFFFFFFFF) y dibujamos una línea vertical de 1px
                context.fill(cx, cy - 1, cx + 1, cy + LINE_HEIGHT - 1, 0xFFFFFFFF);
            }
        }
    }

    private int[] getCursorPosition() {
        String[] lines = this.code.split("\n", -1);
        int pos = 0;
        for (int i = 0; i < lines.length; i++) {
            if (pos + lines[i].length() >= this.cursorPos) {
                return new int[]{i, this.cursorPos - pos};
            }
            pos += lines[i].length() + 1;
        }
        return new int[]{lines.length - 1, lines[lines.length - 1].length()};
    }

    private int countLines() {
        if (this.code.isEmpty()) return 1;
        return this.code.split("\n", -1).length;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isWithinBounds(mouseX, mouseY)) {
            this.setFocused(true);
            updateCursorFromMouse(mouseX, mouseY);
            return true;
        }
        return false;
    }

    private void updateCursorFromMouse(double mouseX, double mouseY) {
        String[] lines = this.code.split("\n", -1);

        // Calcular línea basada en Y
        int relativeY = (int) mouseY - this.getY() - PADDING + (int) this.scrollOffset;
        int lineIndex = relativeY / LINE_HEIGHT;

        if (lineIndex < 0) lineIndex = 0;
        if (lineIndex >= lines.length) lineIndex = lines.length - 1;

        // Calcular columna basada en X
        int relativeX = (int) mouseX - this.getX() - PADDING - LINE_NUMBER_WIDTH;
        String line = lines[lineIndex];

        int col = 0;
        int bestWidth = 0;
        for (int i = 0; i <= line.length(); i++) {
            int width = this.textRenderer.getWidth(line.substring(0, i));
            if (width <= relativeX) {
                col = i;
                bestWidth = width;
            } else {
                break;
            }
        }

        // Calcular posición absoluta del cursor
        int pos = 0;
        for (int i = 0; i < lineIndex; i++) {
            pos += lines[i].length() + 1;
        }
        this.cursorPos = pos + col;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (chr == '\t') {
            String before = this.code.substring(0, this.cursorPos);
            String after = this.code.substring(this.cursorPos);
            this.code = before + "    " + after;
            this.cursorPos += TAB_SIZE;
            return true;
        } else if (chr == '\n' || chr == '\r') {
            String before = this.code.substring(0, this.cursorPos);
            String after = this.code.substring(this.cursorPos);
            this.code = before + "\n" + after;
            this.cursorPos++;
            return true;
        } else if (chr >= 32 && chr < 127) {
            String before = this.code.substring(0, this.cursorPos);
            String after = this.code.substring(this.cursorPos);
            this.code = before + chr + after;
            this.cursorPos++;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE:
                if (this.cursorPos > 0) {
                    String before = this.code.substring(0, this.cursorPos - 1);
                    String after = this.code.substring(this.cursorPos);
                    this.code = before + after;
                    this.cursorPos--;
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_DELETE:
                if (this.cursorPos < this.code.length()) {
                    String before = this.code.substring(0, this.cursorPos);
                    String after = this.code.substring(this.cursorPos + 1);
                    this.code = before + after;
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_LEFT:
                if (this.cursorPos > 0) {
                    this.cursorPos--;
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_RIGHT:
                if (this.cursorPos < this.code.length()) {
                    this.cursorPos++;
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_UP:
                moveCursorVertical(-1);
                return true;
            case GLFW.GLFW_KEY_DOWN:
                moveCursorVertical(1);
                return true;
            case GLFW.GLFW_KEY_HOME:
                moveCursorToLineStart();
                return true;
            case GLFW.GLFW_KEY_END:
                moveCursorToLineEnd();
                return true;
            case GLFW.GLFW_KEY_ENTER:
                return charTyped('\n', 0);
            case GLFW.GLFW_KEY_TAB:
                return charTyped('\t', 0);
        }
        return false;
    }

    private void moveCursorVertical(int direction) {
        String[] lines = this.code.split("\n", -1);
        int[] cursor = getCursorPosition();
        int newLine = cursor[0] + direction;
        if (newLine >= 0 && newLine < lines.length) {
            int targetCol = Math.min(cursor[1], lines[newLine].length());
            int pos = 0;
            for (int i = 0; i < newLine; i++) {
                pos += lines[i].length() + 1;
            }
            this.cursorPos = pos + targetCol;
        }
    }

    private void moveCursorToLineStart() {
        String[] lines = this.code.split("\n", -1);
        int[] cursor = getCursorPosition();
        int pos = 0;
        for (int i = 0; i < cursor[0]; i++) {
            pos += lines[i].length() + 1;
        }
        this.cursorPos = pos;
    }

    private void moveCursorToLineEnd() {
        String[] lines = this.code.split("\n", -1);
        int[] cursor = getCursorPosition();
        int pos = 0;
        for (int i = 0; i <= cursor[0] && i < lines.length; i++) {
            pos += lines[i].length() + 1;
        }
        this.cursorPos = pos - 1;
    }

    @Override
    protected void drawBox(DrawContext context) {
        context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xFF1E1E1E);
        context.drawHorizontalLine(this.getX(), this.getX() + this.width - 1, this.getY(), 0xFF555555);
        context.drawHorizontalLine(this.getX(), this.getX() + this.width - 1, this.getY() + this.height - 1, 0xFF555555);
        context.drawVerticalLine(this.getX(), this.getY(), this.getY() + this.height - 1, 0xFF555555);
        context.drawVerticalLine(this.getX() + this.width - 1, this.getY(), this.getY() + this.height - 1, 0xFF555555);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        // No hay narraciones para el editor
    }

    @Override
    protected boolean isWithinBounds(double x, double y) {
        return x >= this.getX() && x <= this.getX() + this.width
                && y >= this.getY() && y <= this.getY() + this.height;
    }
}
