package textmenu.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import textmenu.interpreter.MCodeInterpreter;

public class TextMenuScreen extends Screen {

    private CodeEditorWidget codeEditor;
    private String outputText = "";

    public TextMenuScreen() {
        super(Text.literal("Editor de Código"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int editorWidth = Math.min(600, this.width - 40);
        int editorHeight = Math.min(300, this.height - 120);

        this.codeEditor = new CodeEditorWidget(
                this.textRenderer,
                centerX - editorWidth / 2,
                40,
                editorWidth,
                editorHeight,
                Text.literal("Editor")
        );
        this.addDrawableChild(this.codeEditor);

        int buttonY = 40 + editorHeight + 10;
        int buttonWidth = 100;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Ejecutar"),
                button -> {
                    String code = this.codeEditor.getCode();
                    this.outputText = MCodeInterpreter.execute(code);
                }
        ).dimensions(centerX - buttonWidth - 5, buttonY, buttonWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Limpiar"),
                button -> {
                    this.codeEditor.setCode("");
                    this.outputText = "";
                }
        ).dimensions(centerX + 5, buttonY, buttonWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Cerrar"),
                button -> this.close()
        ).dimensions(centerX - buttonWidth / 2, buttonY + 25, buttonWidth, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                this.title,
                this.width / 2,
                20,
                0xFFFFFF
        );

        if (!this.outputText.isEmpty()) {
            int outputY = 40 + Math.min(300, this.height - 120) + 55;
            String[] outLines = this.outputText.split("\n");
            int maxLines = Math.max(1, (this.height - outputY - 10) / 12);
            for (int i = 0; i < Math.min(outLines.length, maxLines); i++) {
                int color = this.outputText.startsWith("Error:") ? 0xFF5555 : 0x55FF55;
                context.drawTextWithShadow(
                        this.textRenderer,
                        Text.literal(outLines[i]),
                        this.width / 2 - 150,
                        outputY + (i * 12),
                        color
                );
            }
        }
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (this.codeEditor.isFocused()) {
            return this.codeEditor.charTyped(chr, modifiers);
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.codeEditor.isFocused()) {
            return this.codeEditor.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.codeEditor.setFocused(this.codeEditor.isWithinBounds(mouseX, mouseY));
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
