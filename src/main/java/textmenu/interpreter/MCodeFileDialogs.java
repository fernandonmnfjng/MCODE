package textmenu.interpreter;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cross-platform file picker for MCODE.
 *
 * TinyFD uses native dialogs on Windows/macOS and delegates to the desktop's
 * installed dialog provider on Linux (for example kdialog on KDE Plasma,
 * zenity on GNOME). AWT's native FileDialog is used only as a fallback.
 */
public final class MCodeFileDialogs {
    private static volatile String lastError;

    private MCodeFileDialogs() {}

    /** Opens a native file picker for a Blender-exported Wavefront OBJ model. */
    public static Path chooseOpenModel(Path initialDirectory) {
        return chooseOpenFiltered(initialDirectory, "Importar modelo 3D OBJ", "Modelo Wavefront OBJ (*.obj)", "*.obj", ".obj");
    }

    /** Opens a native file picker for an MCODE project package. */
    public static Path chooseOpenProject(Path initialDirectory) {
        return chooseOpenFiltered(initialDirectory, "Importar proyecto MCODE", "Proyecto MCODE (*.mcp)", "*.mcp", ".mcp");
    }

    /** Chooses an output path for an MCODE project package. */
    public static Path chooseSaveProject(Path initialDirectory, String suggestedName) {
        return chooseSaveFiltered(initialDirectory, "Exportar proyecto MCODE", suggestedName, "Proyecto MCODE (*.mcp)", "*.mcp", ".mcp");
    }

    private static Path chooseOpenFiltered(Path initialDirectory, String title, String description, String pattern, String extension) {
        lastError = null;
        Path directory = usableDirectory(initialDirectory);
        if (directory == null) return null;
        try {
            String selected;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                ByteBuffer encodedPattern = stack.UTF8(pattern);
                filters.put(MemoryUtil.memAddress(encodedPattern));
                filters.flip();
                selected = TinyFileDialogs.tinyfd_openFileDialog(title, withTrailingSeparator(directory), filters, description, false);
            }
            if (selected == null || selected.isBlank()) return null;
            Path path = Path.of(selected).toAbsolutePath().normalize();
            if (!hasExtension(path, extension) || !Files.isRegularFile(path) || !Files.isReadable(path)) {
                lastError = "Selecciona un archivo " + extension + " legible.";
                return null;
            }
            return path;
        } catch (LinkageError | RuntimeException nativeFailure) {
            Path fallback = showAwtDialogFiltered(directory, title, null, FileDialog.LOAD, extension, nativeFailure);
            if (fallback == null) return null;
            if (!Files.isRegularFile(fallback) || !Files.isReadable(fallback)) {
                lastError = "El archivo no existe o no se puede leer: " + fallback;
                return null;
            }
            return fallback;
        }
    }

    private static Path chooseSaveFiltered(Path initialDirectory, String title, String suggestedName, String description, String pattern, String extension) {
        lastError = null;
        Path directory = usableDirectory(initialDirectory);
        if (directory == null) return null;
        String name = suggestedName == null || suggestedName.isBlank() ? "project" + extension : suggestedName;
        if (!name.toLowerCase(Locale.ROOT).endsWith(extension)) name += extension;
        Path suggested = directory.resolve(name).normalize();
        try {
            String selected;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                ByteBuffer encodedPattern = stack.UTF8(pattern);
                filters.put(MemoryUtil.memAddress(encodedPattern));
                filters.flip();
                selected = TinyFileDialogs.tinyfd_saveFileDialog(title, suggested.toString(), filters, description);
            }
            if (selected == null || selected.isBlank()) return null;
            Path path = Path.of(selected).toAbsolutePath().normalize();
            return withExtension(path, extension);
        } catch (LinkageError | RuntimeException nativeFailure) {
            Path fallback = showAwtDialogFiltered(directory, title, name, FileDialog.SAVE, extension, nativeFailure);
            return fallback == null ? null : withExtension(fallback, extension);
        }
    }

    private static Path showAwtDialogFiltered(Path directory, String title, String suggestedName, int mode, String extension, Throwable nativeFailure) {
        AtomicReference<Path> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Runnable task = () -> {
            FileDialog dialog = null;
            try {
                dialog = new FileDialog((Frame) null, title, mode);
                dialog.setDirectory(directory.toString());
                if (suggestedName != null) dialog.setFile(suggestedName);
                if (mode == FileDialog.LOAD) dialog.setFilenameFilter((dir, name) -> name != null && name.toLowerCase(Locale.ROOT).endsWith(extension));
                dialog.setVisible(true);
                String selectedName = dialog.getFile();
                String selectedDirectory = dialog.getDirectory();
                if (selectedName != null && !selectedName.isBlank()) {
                    File selectedFile = selectedDirectory == null || selectedDirectory.isBlank() ? new File(selectedName) : new File(selectedDirectory, selectedName);
                    result.set(selectedFile.toPath().toAbsolutePath().normalize());
                }
            } catch (Throwable ex) { failure.set(ex); }
            finally { if (dialog != null) dialog.dispose(); }
        };
        try {
            if (EventQueue.isDispatchThread()) task.run(); else EventQueue.invokeAndWait(task);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            lastError = "Se interrumpió la apertura del selector de archivos.";
        } catch (InvocationTargetException | RuntimeException ex) {
            Throwable cause = ex instanceof InvocationTargetException && ex.getCause() != null ? ex.getCause() : ex;
            failure.compareAndSet(null, cause);
        }
        if (failure.get() != null) lastError = "No se pudo abrir el selector. LWJGL: " + describe(nativeFailure) + "; selector del sistema: " + describe(failure.get());
        return result.get();
    }

    private static boolean hasExtension(Path path, String extension) {
        return path.getFileName() != null && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension);
    }

    private static Path withExtension(Path path, String extension) {
        Path normalized = path.toAbsolutePath().normalize();
        return hasExtension(normalized, extension) ? normalized : normalized.resolveSibling(normalized.getFileName() + extension);
    }

    /** Returns an error from the last failed picker operation, if any, and clears it. */
    public static String consumeLastError() {
        String error = lastError;
        lastError = null;
        return error;
    }

    public static Path chooseOpen(Path initialDirectory) {
        lastError = null;
        Path directory = usableDirectory(initialDirectory);
        if (directory == null) return null;

        try {
            String selected = showTinyOpen(directory);
            return validateOpenSelection(selected);
        } catch (LinkageError | RuntimeException nativeFailure) {
            Path fallback = showAwtDialog(directory, null, FileDialog.LOAD, nativeFailure);
            return validateOpenPath(fallback);
        }
    }

    public static Path chooseSave(Path initialDirectory, String suggestedName) {
        lastError = null;
        Path directory = usableDirectory(initialDirectory);
        if (directory == null) return null;

        String name = ensureExtension(
                suggestedName == null || suggestedName.isBlank() ? "main.mcode" : suggestedName
        );
        Path suggestedPath = directory.resolve(name).normalize();

        try {
            String selected = showTinySave(suggestedPath);
            return validateSaveSelection(selected);
        } catch (LinkageError | RuntimeException nativeFailure) {
            Path fallback = showAwtDialog(directory, name, FileDialog.SAVE, nativeFailure);
            if (fallback == null) return null;
            return withMcodeExtension(fallback);
        }
    }

    private static String showTinyOpen(Path directory) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = mcodeFilter(stack);
            return TinyFileDialogs.tinyfd_openFileDialog(
                    "Abrir código MCODE",
                    withTrailingSeparator(directory),
                    filters,
                    "Archivos MCODE (*.mcode)",
                    false
            );
        }
    }

    private static String showTinySave(Path suggestedPath) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = mcodeFilter(stack);
            return TinyFileDialogs.tinyfd_saveFileDialog(
                    "Guardar código MCODE",
                    suggestedPath.toString(),
                    filters,
                    "Archivos MCODE (*.mcode)"
            );
        }
    }

    private static PointerBuffer mcodeFilter(MemoryStack stack) {
        ByteBuffer pattern = stack.UTF8("*.mcode");
        PointerBuffer filters = stack.mallocPointer(1);
        filters.put(MemoryUtil.memAddress(pattern));
        filters.flip();
        return filters;
    }

    private static Path validateOpenSelection(String selected) {
        if (selected == null || selected.isBlank()) return null; // User cancelled.
        return validateOpenPath(toPath(selected));
    }

    private static Path validateOpenPath(Path selected) {
        if (selected == null) return null;
        Path normalized = selected.toAbsolutePath().normalize();
        if (!isMcodeFile(normalized)) {
            lastError = "Selecciona un archivo con extensión .mcode.";
            return null;
        }
        if (!Files.isRegularFile(normalized) || !Files.isReadable(normalized)) {
            lastError = "El archivo seleccionado no existe o no se puede leer: " + normalized;
            return null;
        }
        return normalized;
    }

    private static Path validateSaveSelection(String selected) {
        if (selected == null || selected.isBlank()) return null; // User cancelled.
        Path path = toPath(selected);
        if (path == null) return null;
        return withMcodeExtension(path);
    }

    private static Path toPath(String selected) {
        try {
            return Path.of(selected).toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            lastError = "El selector devolvió una ruta no válida: " + ex.getMessage();
            return null;
        }
    }

    private static Path usableDirectory(Path requested) {
        try {
            Path directory = (requested == null
                    ? Path.of(System.getProperty("user.home", "."))
                    : requested).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            if (Files.isDirectory(directory) && Files.isReadable(directory)) return directory;
            lastError = "No se puede acceder a la carpeta de código: " + directory;
        } catch (Exception ex) {
            lastError = "No se pudo preparar la carpeta de código: " + describe(ex);
        }
        return null;
    }

    /** Native AWT picker fallback, dispatched on the AWT event thread. */
    private static Path showAwtDialog(Path directory, String suggestedName, int mode, Throwable nativeFailure) {
        AtomicReference<Path> result = new AtomicReference<>();
        AtomicReference<Throwable> dialogFailure = new AtomicReference<>();

        Runnable task = () -> {
            FileDialog dialog = null;
            try {
                dialog = new FileDialog((Frame) null,
                        mode == FileDialog.LOAD ? "Abrir código MCODE" : "Guardar código MCODE", mode);
                dialog.setDirectory(directory.toString());
                if (suggestedName != null) dialog.setFile(suggestedName);
                if (mode == FileDialog.LOAD) {
                    dialog.setFilenameFilter((dir, name) -> isMcodeFileName(name));
                }
                dialog.setVisible(true);

                String selectedName = dialog.getFile();
                String selectedDirectory = dialog.getDirectory();
                if (selectedName != null && !selectedName.isBlank()) {
                    File selectedFile = selectedDirectory == null || selectedDirectory.isBlank()
                            ? new File(selectedName)
                            : new File(selectedDirectory, selectedName);
                    result.set(selectedFile.toPath().toAbsolutePath().normalize());
                }
            } catch (Throwable ex) {
                dialogFailure.set(ex);
            } finally {
                if (dialog != null) dialog.dispose();
            }
        };

        try {
            if (EventQueue.isDispatchThread()) task.run();
            else EventQueue.invokeAndWait(task);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            lastError = "Se interrumpió la apertura del selector de archivos.";
            return null;
        } catch (InvocationTargetException | RuntimeException ex) {
            Throwable cause = ex instanceof InvocationTargetException && ex.getCause() != null
                    ? ex.getCause() : ex;
            dialogFailure.compareAndSet(null, cause);
        }

        if (dialogFailure.get() != null) {
            lastError = "No se pudo abrir el selector nativo. LWJGL: " + describe(nativeFailure)
                    + "; selector del sistema: " + describe(dialogFailure.get());
        }
        return result.get();
    }

    private static Path withMcodeExtension(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        String fileName = normalized.getFileName() == null ? "" : normalized.getFileName().toString();
        if (isMcodeFileName(fileName)) return normalized;
        return normalized.resolveSibling(fileName + ".mcode");
    }

    private static String ensureExtension(String name) {
        return isMcodeFileName(name) ? name : name + ".mcode";
    }

    private static boolean isMcodeFile(Path path) {
        return path.getFileName() != null && isMcodeFileName(path.getFileName().toString());
    }

    private static boolean isMcodeFileName(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".mcode");
    }

    private static String withTrailingSeparator(Path directory) {
        String path = directory.toString();
        if (path.endsWith(File.separator)) return path;
        return path + File.separator;
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
