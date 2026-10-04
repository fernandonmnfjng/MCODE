package textmenu.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import textmenu.editor.MCodeConsoleWidget;
import textmenu.interpreter.MCodeFileDialogs;
import textmenu.interpreter.MCodeInterpreter;
import textmenu.interpreter.MCodeProjectManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class TextMenuScreen extends Screen {

    private CodeEditorWidget codeEditor;
    private MCodeConsoleWidget consoleWidget;

    public TextMenuScreen() {
        super(Text.literal("MCODE IDE"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int totalWidth = Math.min(640, this.width - 24);
        int startX = centerX - totalWidth / 2;

        int topY = 24;
        int buttonHeight = 20;
        int spacing = 5;

        int availableHeight = this.height - topY - 10;
        int contentHeight = availableHeight - buttonHeight - (spacing * 2);

        int editorHeight = Math.max(70, (int) (contentHeight * 0.62));
        int consoleHeight = Math.max(45, contentHeight - editorHeight);

        int editorY = topY;
        int buttonY = editorY + editorHeight + spacing;
        int consoleY = buttonY + buttonHeight + spacing;

        // Widget de edición de código
        this.codeEditor = new CodeEditorWidget(
                startX,
                editorY,
                totalWidth,
                editorHeight,
                Text.literal("Editor"),
                this.textRenderer
        );
        this.addDrawableChild(this.codeEditor);

        // Barra de botones
        int buttonWidth = 78;
        int buttonSpacing = 5;
        int buttonsTotalWidth = (buttonWidth * 5) + (buttonSpacing * 4);
        int buttonStartX = centerX - (buttonsTotalWidth / 2);

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("▶ Ejecutar"),
                button -> executeCode()
        ).dimensions(buttonStartX, buttonY, buttonWidth, buttonHeight).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("📂 Abrir"),
                button -> openFile()
        ).dimensions(buttonStartX + (buttonWidth + buttonSpacing), buttonY, buttonWidth, buttonHeight).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("💾 Guardar"),
                button -> saveFile()
        ).dimensions(buttonStartX + (buttonWidth + buttonSpacing) * 2, buttonY, buttonWidth, buttonHeight).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("🧹 Limpiar"),
                button -> {
                    this.codeEditor.setCode("");
                    this.consoleWidget.clearOutput();
                }
        ).dimensions(buttonStartX + (buttonWidth + buttonSpacing) * 3, buttonY, buttonWidth, buttonHeight).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("✖ Cerrar"),
                button -> this.close()
        ).dimensions(buttonStartX + (buttonWidth + buttonSpacing) * 4, buttonY, buttonWidth, buttonHeight).build());

        // Consola de salida de MCODE
        this.consoleWidget = new MCodeConsoleWidget(
                startX,
                consoleY,
                totalWidth,
                consoleHeight,
                Text.literal("Consola"),
                this.textRenderer
        );
        this.addDrawableChild(this.consoleWidget);
    }

    private void executeCode() {
        String code = this.codeEditor.getCode();
        String output = MCodeInterpreter.execute(code);
        if (output == null || output.isEmpty()) {
            this.consoleWidget.setOutput("✓ Código ejecutado correctamente (sin salida)");
        } else {
            this.consoleWidget.setOutput(output);
        }
    }

    private void openFile() {
        Path chosen = MCodeFileDialogs.chooseOpen(MCodeProjectManager.getWorkspaceRoot());
        if (chosen != null) {
            try {
                String content = Files.readString(chosen, StandardCharsets.UTF_8);
                this.codeEditor.setCode(content);
                this.consoleWidget.setOutput("✓ Archivo abierto: " + chosen.getFileName());
            } catch (Exception ex) {
                this.consoleWidget.setOutput("Error al abrir archivo: " + ex.getMessage());
            }
        }
    }

    private void saveFile() {
        Path chosen = MCodeFileDialogs.chooseSave(MCodeProjectManager.getWorkspaceRoot(), "main.mcode");
        if (chosen != null) {
            try {
                Files.writeString(chosen, this.codeEditor.getCode(), StandardCharsets.UTF_8);
                this.consoleWidget.setOutput("✓ Archivo guardado: " + chosen.getFileName());
            } catch (Exception ex) {
                this.consoleWidget.setOutput("Error al guardar archivo: " + ex.getMessage());
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                this.title,
                this.width / 2,
                9,
                0xFFFFFF
        );
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
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;

        // Atajos globales
        if (keyCode == GLFW.GLFW_KEY_F5 || (keyCode == GLFW.GLFW_KEY_ENTER && ctrl)) {
            executeCode();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_S && ctrl) {
            saveFile();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_O && ctrl) {
            openFile();
            return true;
        }

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
