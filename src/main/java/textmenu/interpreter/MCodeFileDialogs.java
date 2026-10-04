package textmenu.interpreter;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;

/**
 * Cross-platform native file dialogs using LWJGL TinyFileDialogs.
 *
 * Works on Windows, Linux (GTK / zenity / kdialog) and macOS without
 * any extra native library — LWJGL is already bundled with Minecraft.
 *
 * IMPORTANT: Must be called from the Minecraft render thread (the thread
 * that owns the GLFW window). Calling it from any other thread will crash.
 */
public final class MCodeFileDialogs {

    private static final String[] FILTER_PATTERNS = { "*.mcode" };
    private static final String FILTER_DESC = "MCODE files (*.mcode)";

    private MCodeFileDialogs() {}

    /**
     * Opens a native "Open File" dialog and returns the selected path,
     * or {@code null} if the user cancelled.
     *
     * @param initialDirectory Starting directory shown in the dialog.
     */
    public static Path chooseOpen(Path initialDirectory) {
        // Make sure Minecraft's window doesn't intercept the focus while
        // the dialog is open.
        releaseMouseCapture();

        String initial = initialDirectory != null
                ? initialDirectory.toAbsolutePath().toString()
                : null;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = buildFilters(stack);
            String result = TinyFileDialogs.tinyfd_openFileDialog(
                    "Abrir archivo MCODE",
                    initial,
                    filters,
                    FILTER_DESC,
                    false
            );
            if (result == null || result.isBlank()) return null;
            Path chosen = Path.of(result).toAbsolutePath().normalize();
            return ensureExtension(chosen);
        }
    }

    /**
     * Opens a native "Save File" dialog and returns the selected path,
     * or {@code null} if the user cancelled.
     *
     * @param initialDirectory Starting directory shown in the dialog.
     * @param suggestedName    Default file name pre-filled in the dialog.
     */
    public static Path chooseSave(Path initialDirectory, String suggestedName) {
        releaseMouseCapture();

        String name = (suggestedName != null && !suggestedName.isBlank())
                ? ensureExtensionString(suggestedName)
                : "main.mcode";

        String initial = initialDirectory != null
                ? initialDirectory.toAbsolutePath().resolve(name).toString()
                : name;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = buildFilters(stack);
            String result = TinyFileDialogs.tinyfd_saveFileDialog(
                    "Guardar archivo MCODE",
                    initial,
                    filters,
                    FILTER_DESC
            );
            if (result == null || result.isBlank()) return null;
            Path chosen = Path.of(result).toAbsolutePath().normalize();
            return ensureExtension(chosen);
        }
    }

    // ---- helpers --------------------------------------------------------

    /**
     * Builds a PointerBuffer pointing to the filter patterns string array
     * using the current MemoryStack frame.
     */
    private static PointerBuffer buildFilters(MemoryStack stack) {
        PointerBuffer pb = stack.mallocPointer(FILTER_PATTERNS.length);
        for (String pattern : FILTER_PATTERNS) {
            pb.put(stack.UTF8(pattern));
        }
        pb.flip();
        return pb;
    }

    private static Path ensureExtension(Path path) {
        String name = path.getFileName().toString();
        if (!name.toLowerCase().endsWith(".mcode")) {
            path = path.getParent().resolve(name + ".mcode");
        }
        return path;
    }

    private static String ensureExtensionString(String name) {
        return name.toLowerCase().endsWith(".mcode") ? name : name + ".mcode";
    }

    /**
     * Releases Minecraft's mouse capture so the native OS dialog can
     * receive mouse and keyboard events normally.
     */
    private static void releaseMouseCapture() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.mouse != null) {
            // unlockCursor() releases GLFW's mouse grab so the dialog works.
            mc.mouse.unlockCursor();
        }
    }
}
