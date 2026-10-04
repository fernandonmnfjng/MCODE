package textmenu.interpreter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Filesystem for MCODE source files.
 * Default workspace: ~/.mcode
 * Minecraft should set it to <minecraft run directory>/mcode.
 */
public final class MCodeProjectManager {
    private static volatile Path workspaceRoot =
            Path.of(System.getProperty("user.home"), ".mcode").toAbsolutePath().normalize();

    private MCodeProjectManager() {}

    public static void setWorkspaceRoot(Path root) {
        if (root == null) throw new IllegalArgumentException("workspaceRoot cannot be null");
        workspaceRoot = root.toAbsolutePath().normalize();
    }

    public static Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    public static void initialize() throws IOException {
        Files.createDirectories(workspaceRoot);
    }

    public static Path normalizeSourcePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Ruta vacía");
        }

        String clean = relativePath.replace('\\', '/');
        if (!clean.toLowerCase(Locale.ROOT).endsWith(".mcode")) {
            clean += ".mcode";
        }

        Path result = workspaceRoot.resolve(clean).normalize();
        if (!result.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("Ruta fuera del workspace");
        }
        return result;
    }

    public static Path save(String relativePath, String source) throws IOException {
        Path file = normalizeSourcePath(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(
                file,
                source == null ? "" : source,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
        return file;
    }

    public static String load(String relativePath) throws IOException {
        return Files.readString(normalizeSourcePath(relativePath), StandardCharsets.UTF_8);
    }

    public static boolean exists(String relativePath) {
        return Files.isRegularFile(normalizeSourcePath(relativePath));
    }

    public static List<Path> listSourceFiles() throws IOException {
        initialize();
        try (var stream = Files.walk(workspaceRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mcode"))
                    .sorted()
                    .toList();
        }
    }

    public static String relativeName(Path file) {
        return workspaceRoot.relativize(file.toAbsolutePath().normalize())
                .toString()
                .replace(FileSystems.getDefault().getSeparator(), "/");
    }

    /** Resolves import foo.bar -> workspace/foo/bar.mcode. */
    public static Path resolveModule(String moduleName) {
        if (moduleName == null || moduleName.isBlank()) return null;
        String clean = moduleName.replace('.', '/')
                .replace('\\', '/');
        Path path = workspaceRoot.resolve(clean + ".mcode").normalize();
        if (path.startsWith(workspaceRoot) && Files.isRegularFile(path)) return path;

        Path init = workspaceRoot.resolve(clean).resolve("__init__.mcode").normalize();
        if (init.startsWith(workspaceRoot) && Files.isRegularFile(init)) return init;
        return null;
    }

    public static String moduleNameFromPath(Path path) {
        String rel = relativeName(path);
        if (rel.endsWith(".mcode")) rel = rel.substring(0, rel.length() - 6);
        if (rel.endsWith("/__init__")) rel = rel.substring(0, rel.length() - 9);
        return rel.replace('/', '.').replace('\\', '.');
    }
}
