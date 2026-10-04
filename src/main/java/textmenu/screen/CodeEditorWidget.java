package textmenu.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ScrollableWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import textmenu.editor.MCodeEditorKeyHandler;
import textmenu.editor.MCodeEditorModel;
import textmenu.interpreter.MCodeSyntaxHighlighter;

public class CodeEditorWidget extends ScrollableWidget {

    private final TextRenderer textRenderer;
    private final MCodeEditorModel model = new MCodeEditorModel();
    private long lastInteractionTime = System.currentTimeMillis();
    private int lastRenderedCursorPos = -1;

    private static final int LINE_HEIGHT = 12;
    private static final int PADDING = 4;
    private static final int LINE_NUMBER_WIDTH = 35;

    public CodeEditorWidget(TextRenderer textRenderer, int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
        this.textRenderer = textRenderer;
    }

    public MCodeEditorModel getModel() {
        return this.model;
    }

    public String getCode() {
        return this.model.getText();
    }

    public void setCode(String code) {
        this.model.setText(code);
        this.setScrollY(0);
    }

    @Override
    protected int getContentsHeight() {
        int lines = countLines();
        return lines * LINE_HEIGHT + PADDING * 2;
    }

    @Override
    protected double getDeltaYPerScroll() {
        return LINE_HEIGHT * 2.0;
    }

    @Override
    protected void renderContents(DrawContext context, int mouseX, int mouseY, float delta) {
        String code = this.model.getText();
        String[] lines = code.split("\n", -1);
        int scrollY = (int) this.getScrollY();
        int startLine = scrollY / LINE_HEIGHT;
        int visibleLines = (this.height - PADDING * 2) / LINE_HEIGHT + 1;

        int selStart = this.model.selectionStart();
        int selEnd = this.model.selectionEnd();
        boolean hasSelection = this.model.hasSelection();

        int charOffset = 0;
        for (int i = 0; i < startLine && i < lines.length; i++) {
            charOffset += lines[i].length() + 1;
        }

        for (int i = 0; i <= visibleLines && startLine + i < lines.length; i++) {
            int lineIndex = startLine + i;
            int y = this.getY() + PADDING + lineIndex * LINE_HEIGHT - scrollY;
            String line = lines[lineIndex];
            int lineStartChar = charOffset;
            int lineEndChar = lineStartChar + line.length();

            // Número de línea
            context.drawTextWithShadow(
                    this.textRenderer,
                    Text.literal(String.format("%3d |", lineIndex + 1)),
                    this.getX() + PADDING,
                    y,
                    0x888888
            );

            // Resaltado de selección
            if (hasSelection && selStart < lineEndChar && selEnd > lineStartChar) {
                int startCol = Math.max(0, selStart - lineStartChar);
                int endCol = Math.min(line.length(), selEnd - lineStartChar);
                int sx1 = this.getX() + PADDING + LINE_NUMBER_WIDTH + this.textRenderer.getWidth(line.substring(0, startCol));
                int sx2 = this.getX() + PADDING + LINE_NUMBER_WIDTH + this.textRenderer.getWidth(line.substring(0, endCol));
                if (selEnd > lineEndChar) {
                    sx2 += 6;
                }
                context.fill(sx1, y, sx2, y + LINE_HEIGHT, 0x66264F78);
            }

            // Resaltado de sintaxis
            if (!line.isEmpty()) {
                MCodeSyntaxHighlighter.drawLine(
                        context,
                        this.textRenderer,
                        line,
                        this.getX() + PADDING + LINE_NUMBER_WIDTH,
                        y
                );
            }

            charOffset = lineEndChar + 1;
        }

        // Cursor parpadeante
        int cursorPos = this.model.getCursor();
        if (cursorPos != this.lastRenderedCursorPos) {
            this.lastInteractionTime = System.currentTimeMillis();
            this.lastRenderedCursorPos = cursorPos;
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
                int cy = this.getY() + PADDING + cursor[0] * LINE_HEIGHT - scrollY;
                context.fill(cx, cy - 1, cx + 1, cy + LINE_HEIGHT - 1, 0xFFFFFFFF);
            }
        }
    }

    private int[] getCursorPosition() {
        String code = this.model.getText();
        int cursorPos = this.model.getCursor();
        String[] lines = code.split("\n", -1);
        int pos = 0;
        for (int i = 0; i < lines.length; i++) {
            if (pos + lines[i].length() >= cursorPos) {
                return new int[]{i, cursorPos - pos};
            }
            pos += lines[i].length() + 1;
        }
        return new int[]{lines.length - 1, lines[lines.length - 1].length()};
    }

    private int countLines() {
        String code = this.model.getText();
        if (code.isEmpty()) return 1;
        return code.split("\n", -1).length;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isWithinBounds(mouseX, mouseY)) {
            this.setFocused(true);
            long window = MinecraftClient.getInstance().getWindow().getHandle();
            boolean shift = (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS)
                    || (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS);
            updateCursorFromMouse(mouseX, mouseY, shift);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && isWithinBounds(mouseX, mouseY)) {
            updateCursorFromMouse(mouseX, mouseY, true);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private void updateCursorFromMouse(double mouseX, double mouseY, boolean keepSelection) {
        String code = this.model.getText();
        String[] lines = code.split("\n", -1);
        int scrollY = (int) this.getScrollY();

        int relativeY = (int) mouseY - this.getY() - PADDING + scrollY;
        int lineIndex = relativeY / LINE_HEIGHT;

        if (lineIndex < 0) lineIndex = 0;
        if (lineIndex >= lines.length) lineIndex = lines.length - 1;

        int relativeX = (int) mouseX - this.getX() - PADDING - LINE_NUMBER_WIDTH;
        String line = lines[lineIndex];

        int col = 0;
        for (int i = 0; i <= line.length(); i++) {
            int width = this.textRenderer.getWidth(line.substring(0, i));
            if (width <= relativeX) {
                col = i;
            } else {
                break;
            }
        }

        int pos = 0;
        for (int i = 0; i < lineIndex; i++) {
            pos += lines[i].length() + 1;
        }
        this.model.setCursor(pos + col, keepSelection);
        this.lastInteractionTime = System.currentTimeMillis();
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!this.isFocused()) return false;
        if (this.model.typeCharacter(chr)) {
            this.lastInteractionTime = System.currentTimeMillis();
            ensureCursorVisible();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.isFocused()) return false;

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            this.model.typeCharacter('\n');
            this.lastInteractionTime = System.currentTimeMillis();
            ensureCursorVisible();
            return true;
        }

        int visibleLines = (this.height - PADDING * 2) / LINE_HEIGHT;
        if (MCodeEditorKeyHandler.handle(this.model, keyCode, modifiers, MinecraftClient.getInstance(), visibleLines)) {
            this.lastInteractionTime = System.currentTimeMillis();
            ensureCursorVisible();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void ensureCursorVisible() {
        int[] cursor = getCursorPosition();
        if (cursor != null) {
            int cursorY = cursor[0] * LINE_HEIGHT;
            int viewHeight = this.height - PADDING * 2;
            double currentScroll = this.getScrollY();
            if (cursorY < currentScroll) {
                this.setScrollY(cursorY);
            } else if (cursorY + LINE_HEIGHT > currentScroll + viewHeight) {
                this.setScrollY(cursorY + LINE_HEIGHT - viewHeight);
            }
        }
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
    }

    public boolean isWithinBounds(double x, double y) {
        return x >= this.getX() && x <= this.getX() + this.width
                && y >= this.getY() && y <= this.getY() + this.height;
    }
}
