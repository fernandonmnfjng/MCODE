package textmenu.editor;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ScrollableWidget;
import net.minecraft.text.Text;

/**
 * Read-only multiline MCODE console.
 * It displays every output line and provides vertical scrolling, but it has
 * no text input, selection or editing behavior.
 */
public final class MCodeConsoleWidget extends ScrollableWidget {

    private static final int BACKGROUND = 0xE6090B10;
    private static final int BORDER = 0xFF252A33;
    private static final int TEXT = 0xFFD4D4D4;
    private static final int ERROR = 0xFFFF6B6B;

    private final TextRenderer textRenderer;
    private final MCodeConsoleModel console = new MCodeConsoleModel();

    private int lineHeight = 10;
    private int padding = 6;

    public MCodeConsoleWidget(
            int x,
            int y,
            int width,
            int height,
            Text title,
            TextRenderer textRenderer
    ) {
        super(x, y, width, height, title);
        this.textRenderer = textRenderer;
    }

    public void setOutput(String output) {
        console.setText(output);
        setScrollY(Math.max(0, console.lineCount() * lineHeight));
    }

    public void appendOutput(String output) {
        console.append(output);
        setScrollY(Math.max(0, console.lineCount() * lineHeight));
    }

    public void appendLine(String line) {
        console.appendLine(line);
        setScrollY(Math.max(0, console.lineCount() * lineHeight));
    }

    public void clearOutput() {
        console.clear();
        setScrollY(0);
    }

    public String getOutput() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < console.lineCount(); i++) {
            if (i > 0) result.append('\n');
            result.append(console.getLine(i));
        }
        return result.toString();
    }

    public MCodeConsoleModel getConsole() {
        return console;
    }

    @Override
    protected void renderContents(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta
    ) {
        int x = getX() + padding;
        int y = getY() + padding;

        for (int i = 0; i < console.lineCount(); i++) {
            String line = console.getLine(i);
            int lineY = y + i * lineHeight;
            int color = line.startsWith("Error:") ? ERROR : TEXT;

            context.drawText(
                    textRenderer,
                    line,
                    x,
                    lineY,
                    color,
                    false
            );
        }
    }

    @Override
    protected void renderOverlay(DrawContext context) {
        super.renderOverlay(context);
        int x = getX();
        int y = getY();
        int right = x + getWidth() - 1;
        int bottom = y + getHeight() - 1;

        context.fill(x, y, right, y + 1, BORDER);
        context.fill(x, bottom - 1, right, bottom, BORDER);
        context.fill(x, y, x + 1, bottom, BORDER);
        context.fill(right - 1, y, right, bottom, BORDER);
    }

    @Override
    public int getContentsHeight() {
        return Math.max(getHeight(), padding * 2 + console.lineCount() * lineHeight);
    }

    @Override
    protected double getDeltaYPerScroll() {
        return lineHeight * 3.0;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(NarrationPart.TITLE, Text.literal("MCODE Console"));
    }

    @Override
    public void renderWidget(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta
    ) {
        context.fill(
                getX(),
                getY(),
                getX() + getWidth(),
                getY() + getHeight(),
                BACKGROUND
        );
        super.renderWidget(context, mouseX, mouseY, delta);
    }
}
