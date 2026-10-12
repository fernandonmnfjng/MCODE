package textmenu.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import textmenu.interpreter.MCodeHelp;

import java.util.ArrayList;
import java.util.List;

/** In-game, paged help for editor APIs and shortcuts. */
public final class MCodeHelpScreen extends Screen {
    private final Screen parent;
    private final List<String> pages = MCodeHelp.pages();
    private int page;
    private List<String> visibleLines = List.of();
    private ButtonWidget previousButton;
    private ButtonWidget nextButton;

    public MCodeHelpScreen(Screen parent) {
        super(Text.literal("Ayuda de MCODE"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearChildren();
        previousButton = ButtonWidget.builder(Text.literal("Anterior"), b -> { page = Math.max(0, page - 1); rebuildLines(); rebuildControls(); })
                .dimensions(width / 2 - 155, height - 30, 90, 20).build();
        addDrawableChild(previousButton);
        nextButton = ButtonWidget.builder(Text.literal("Siguiente"), b -> { page = Math.min(pages.size() - 1, page + 1); rebuildLines(); rebuildControls(); })
                .dimensions(width / 2 - 45, height - 30, 90, 20).build();
        addDrawableChild(nextButton);
        addDrawableChild(ButtonWidget.builder(Text.literal("Volver"), b -> client.setScreen(parent))
                .dimensions(width / 2 + 65, height - 30, 90, 20).build());
        rebuildLines();
        rebuildControls();
    }

    private void rebuildControls() {
        if (previousButton != null) previousButton.active = page > 0;
        if (nextButton != null) nextButton.active = page < pages.size() - 1;
    }

    private void rebuildLines() {
        List<String> result = new ArrayList<>();
        int maxWidth = Math.max(200, width - 48);
        for (String paragraph : pages.get(page).split("\\n", -1)) {
            if (paragraph.isEmpty()) { result.add(""); continue; }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split("\\s+")) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && textRenderer.getWidth(next) > maxWidth) {
                    result.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                } else {
                    if (!line.isEmpty()) line.append(' ');
                    line.append(word);
                }
            }
            if (!line.isEmpty()) result.add(line.toString());
        }
        visibleLines = result;
    }

    @Override
    public void resize(net.minecraft.client.MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        rebuildLines();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, "Página " + (page + 1) + " / " + pages.size(), width / 2, 25, 0x8FB9FF);
        int y = 44;
        int bottom = height - 40;
        for (String line : visibleLines) {
            if (y + 10 > bottom) break;
            context.drawText(textRenderer, line, 24, y, line.startsWith("MCODE") ? 0xFF8FB9FF : 0xFFD8DEE9, false);
            y += 11;
        }
    }
}
