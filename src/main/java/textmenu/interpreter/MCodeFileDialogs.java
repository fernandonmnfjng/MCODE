package textmenu.interpreter;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.EventQueue;
import java.nio.file.Path;

/** Small native file dialogs for the MCODE Save/Open buttons. */
public final class MCodeFileDialogs {
    private MCodeFileDialogs() {}

    public static Path chooseOpen(Path initialDirectory) {
        final Path[] result = {null};
        Runnable task = () -> {
            JFileChooser chooser = new JFileChooser(initialDirectory.toFile());
            chooser.setDialogTitle("Open MCODE file");
            chooser.setFileFilter(new FileNameExtensionFilter("MCODE files (*.mcode)", "mcode"));
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                Path selected = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
                if (selected.getFileName().toString().toLowerCase().endsWith(".mcode")) result[0] = selected;
            }
        };
        runAndWait(task);
        return result[0];
    }

    public static Path chooseSave(Path initialDirectory, String suggestedName) {
        final Path[] result = {null};
        Runnable task = () -> {
            JFileChooser chooser = new JFileChooser(initialDirectory.toFile());
            chooser.setDialogTitle("Save MCODE file");
            chooser.setFileFilter(new FileNameExtensionFilter("MCODE files (*.mcode)", "mcode"));
            chooser.setSelectedFile(initialDirectory.resolve(
                    suggestedName == null || suggestedName.isBlank() ? "Code.mcode" : ensureExtension(suggestedName)
            ).toFile());
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                Path selected = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
                result[0] = Path.of(ensureExtension(selected.toString()));
            }
        };
        runAndWait(task);
        return result[0];
    }

    private static String ensureExtension(String name) {
        return name.toLowerCase().endsWith(".mcode") ? name : name + ".mcode";
    }

    private static void runAndWait(Runnable task) {
        try {
            if (EventQueue.isDispatchThread()) task.run();
            else EventQueue.invokeAndWait(task);
        } catch (Exception ignored) {
        }
    }
}
