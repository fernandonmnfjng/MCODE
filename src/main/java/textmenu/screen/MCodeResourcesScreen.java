package textmenu.screen;

import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import textmenu.interpreter.MCodeFileDialogs;
import textmenu.interpreter.MCodeProjectArchive;
import textmenu.interpreter.MCodeProjectManager;
import textmenu.runtime.ScriptRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Import/export entrypoint for models and multi-file MCODE projects. */
public final class MCodeResourcesScreen extends Screen {
    private final TextMenuScreen parent;

    public MCodeResourcesScreen(TextMenuScreen parent) {
        super(Text.literal("Recursos y proyectos MCODE"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(320, width - 36);
        int x = (width - buttonWidth) / 2;
        int y = Math.max(52, height / 2 - 72);
        addDrawableChild(ButtonWidget.builder(Text.literal("Importar modelo Blender (OBJ)"), b -> importModel())
                .dimensions(x, y, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Importar proyecto MCODE (.mcp)"), b -> importProject())
                .dimensions(x, y + 26, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Exportar proyecto MCODE (.mcp)"), b -> exportProject())
                .dimensions(x, y + 52, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Ayuda de API y ejemplos"), b -> client.setScreen(new MCodeHelpScreen(this)))
                .dimensions(x, y + 78, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Volver al editor"), b -> client.setScreen(parent))
                .dimensions(x, y + 110, buttonWidth, 20).build());
    }

    private void importModel() {
        Path selected = MCodeFileDialogs.chooseOpenModel(MCodeProjectManager.getWorkspaceRoot());
        if (selected == null) {
            reportPickerError("Importar modelo");
            return;
        }
        String filename = selected.getFileName().toString();
        Path target;
        try {
            target = MCodeProjectManager.normalizeDataPath("models/" + filename);
            Files.createDirectories(target.getParent());
        } catch (Exception ex) {
            report("No se pudo preparar la carpeta de modelos: " + ex.getMessage());
            return;
        }
        if (Files.exists(target)) {
            client.setScreen(new ConfirmScreen(confirmed -> {
                client.setScreen(this);
                if (confirmed) copyModel(selected, target);
            }, Text.literal("Sobrescribir modelo"),
                    Text.literal("Ya existe models/" + filename + ". ¿Deseas reemplazarlo?")));
        } else copyModel(selected, target);
    }

    private void copyModel(Path source, Path target) {
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            String relative = MCodeProjectManager.getWorkspaceRoot().relativize(target).toString().replace('\\', '/');
            report("Modelo importado: " + relative + "\nPrueba en un script: model = mc.import_model(\"" + relative + "\", 0, 0, 4, scale=1, axis=\"blender\", color=\"cyan\")");
            client.setScreen(parent);
        } catch (Exception ex) {
            report("Error al copiar el modelo: " + ex.getMessage());
        }
    }

    private void importProject() {
        Path selected = MCodeFileDialogs.chooseOpenProject(MCodeProjectManager.getWorkspaceRoot());
        if (selected == null) {
            reportPickerError("Importar proyecto");
            return;
        }
        try {
            MCodeProjectArchive.ImportPreview preview = MCodeProjectArchive.inspect(selected);
            if (!preview.conflicts().isEmpty()) {
                String details = preview.conflicts().size() + " archivos ya existen y podrían sobrescribirse.\n" +
                        "Sí: sobrescribir los existentes. No: conservar los existentes y saltarlos.\n" +
                        "Archivos que se importarán: " + preview.files().size();
                client.setScreen(new ConfirmScreen(overwrite -> {
                    client.setScreen(this);
                    try {
                        MCodeProjectArchive.ImportResult result = MCodeProjectArchive.importArchive(selected, overwrite);
                        report("Proyecto '" + result.projectName() + "' importado: " + result.imported() + " nuevos, " + result.overwritten() + " sobrescritos, " + result.skipped() + " omitidos.");
                        client.setScreen(parent);
                    } catch (Exception ex) { report("Error al importar proyecto: " + ex.getMessage()); }
                }, Text.literal("Importar proyecto " + preview.projectName()), Text.literal(details)));
            } else {
                MCodeProjectArchive.ImportResult result = MCodeProjectArchive.importArchive(selected, false);
                report("Proyecto '" + result.projectName() + "' instalado: " + result.imported() + " archivos.");
                client.setScreen(parent);
            }
        } catch (Exception ex) {
            report("No se pudo importar el proyecto: " + ex.getMessage());
        }
    }

    private void exportProject() {
        Path selected = MCodeFileDialogs.chooseSaveProject(MCodeProjectManager.getWorkspaceRoot(), "proyecto-mcode.mcp");
        if (selected == null) {
            reportPickerError("Exportar proyecto");
            return;
        }
        if (Files.exists(selected)) {
            client.setScreen(new ConfirmScreen(overwrite -> {
                client.setScreen(this);
                if (overwrite) writeProject(selected);
            }, Text.literal("Sobrescribir paquete"), Text.literal("El archivo ya existe. ¿Deseas reemplazarlo?")));
        } else writeProject(selected);
    }

    private void writeProject(Path path) {
        try {
            int count = MCodeProjectArchive.exportWorkspace(path);
            report("Proyecto exportado con " + count + " archivos: " + path.getFileName());
            client.setScreen(parent);
        } catch (Exception ex) { report("No se pudo exportar el proyecto: " + ex.getMessage()); }
    }

    private void reportPickerError(String action) {
        String error = MCodeFileDialogs.consumeLastError();
        if (error != null) report(action + ": " + error);
    }

    private void report(String message) {
        ScriptRunner.console().append(message + "\n");
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, "Modelos OBJ, proyectos .mcp y ayuda", width / 2, 31, 0x9AA4B2);
    }
}
