package textmenu.interpreter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Safe .mcp project packager. Packages contain MCODE source and passive assets, never executable binaries. */
public final class MCodeProjectArchive {
    public static final String EXTENSION = ".mcp";
    public static final String MANIFEST = "mcode-project.json";
    public static final int MAX_FILES = 10_000;
    public static final long MAX_TOTAL_UNPACKED = 256L * 1024L * 1024L;
    public static final long MAX_SINGLE_FILE = 64L * 1024L * 1024L;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".mcode", ".obj", ".mtl", ".png", ".jpg", ".jpeg", ".webp", ".tga", ".bmp", ".tif", ".tiff",
            ".dds", ".exr", ".hdr", ".gltf", ".glb", ".bin", ".blend", ".ttf", ".otf",
            ".json", ".txt", ".md", ".csv", ".dat", ".toml", ".properties", ".mcmeta", ".wav", ".ogg",
            ".frag", ".vert", ".glsl", ".xml", ".yml", ".yaml", ".ini"
    );
    private static final Set<String> NEVER_IMPORT = Set.of(
            ".mcp", ".jar", ".class", ".exe", ".dll", ".so", ".dylib", ".bat", ".cmd", ".sh", ".ps1", ".pyc"
    );

    private MCodeProjectArchive() {}

    public record ImportPreview(String projectName, List<String> files, List<String> conflicts, long unpackedBytes) {}
    public record ImportResult(int imported, int overwritten, int skipped, String projectName) {}

    /** Validates paths, types, duplicate names and actual uncompressed sizes before showing a confirmation. */
    public static ImportPreview inspect(Path archive) throws IOException {
        List<String> files = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        Set<String> uniqueNames = new HashSet<>();
        long total = 0;
        boolean manifestSeen = false;
        String projectName = archive.getFileName() == null ? "Proyecto MCODE" : archive.getFileName().toString().replaceFirst("(?i)\\.mcp$", "");

        try (ZipFile zip = new ZipFile(archive.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = safeRelativeName(entry.getName());
                if (name.equals(MANIFEST)) {
                    if (manifestSeen) throw new IOException("El paquete contiene más de un manifest.");
                    manifestSeen = true;
                    byte[] bytes;
                    try (InputStream in = zip.getInputStream(entry)) { bytes = readBounded(in, 1024L * 1024L, "Manifest demasiado grande."); }
                    String parsedName = readManifestName(new String(bytes, StandardCharsets.UTF_8));
                    if (parsedName != null) projectName = parsedName;
                    continue;
                }
                if (!isProjectFile(name)) continue;
                String key = name.toLowerCase(Locale.ROOT);
                if (!uniqueNames.add(key)) throw new IOException("El paquete repite una ruta (sin distinguir mayúsculas): " + name);
                long actualSize;
                try (InputStream in = zip.getInputStream(entry)) { actualSize = readBoundedCount(in, MAX_SINGLE_FILE, "Archivo demasiado grande: " + name); }
                total += actualSize;
                if (total > MAX_TOTAL_UNPACKED) throw new IOException("El proyecto supera el límite de 256 MiB descomprimidos.");
                files.add(name);
                Path destination = MCodeProjectManager.normalizeDataPath(name);
                if (Files.exists(destination)) conflicts.add(name);
                if (files.size() > MAX_FILES) throw new IOException("El proyecto supera 10.000 archivos.");
            }
        } catch (IllegalArgumentException ex) {
            throw new IOException("La ruta del archivo dentro del proyecto no es segura: " + ex.getMessage(), ex);
        }
        if (files.isEmpty()) throw new IOException("El archivo no contiene scripts ni recursos compatibles de MCODE.");
        files.sort(String.CASE_INSENSITIVE_ORDER);
        conflicts.sort(String.CASE_INSENSITIVE_ORDER);
        return new ImportPreview(projectName, List.copyOf(files), List.copyOf(conflicts), total);
    }

    /** Extracts first into a private staging directory, then copies only validated files to the workspace. */
    public static ImportResult importArchive(Path archive, boolean overwrite) throws IOException {
        ImportPreview preview = inspect(archive);
        Path staging = Files.createTempDirectory("mcode-project-import-");
        int imported = 0, overwritten = 0, skipped = 0;
        try {
            long totalCopied = 0;
            try (ZipFile zip = new ZipFile(archive.toFile())) {
                for (String name : preview.files()) {
                    ZipEntry entry = zip.getEntry(name);
                    if (entry == null || entry.isDirectory()) throw new IOException("Falta el archivo esperado en el paquete: " + name);
                    Path staged = staging.resolve(name).normalize();
                    if (!staged.startsWith(staging)) throw new IOException("Ruta insegura dentro del paquete: " + name);
                    Files.createDirectories(staged.getParent());
                    try (InputStream in = zip.getInputStream(entry); OutputStream out = Files.newOutputStream(staged)) {
                        byte[] buffer = new byte[8192];
                        long singleCopied = 0;
                        int count;
                        while ((count = in.read(buffer)) != -1) {
                            singleCopied += count;
                            totalCopied += count;
                            if (singleCopied > MAX_SINGLE_FILE) throw new IOException("Archivo supera 64 MiB durante extracción: " + name);
                            if (totalCopied > MAX_TOTAL_UNPACKED) throw new IOException("El paquete supera 256 MiB durante extracción.");
                            out.write(buffer, 0, count);
                        }
                    }
                }
            }

            Path root = MCodeProjectManager.getWorkspaceRoot().toAbsolutePath().normalize();
            for (String name : preview.files()) {
                Path target = MCodeProjectManager.normalizeDataPath(name).toAbsolutePath().normalize();
                if (!target.startsWith(root)) throw new IOException("Ruta fuera del workspace: " + name);
                boolean exists = Files.exists(target);
                if (exists && !overwrite) { skipped++; continue; }
                Files.createDirectories(target.getParent());
                Files.copy(staging.resolve(name), target, StandardCopyOption.REPLACE_EXISTING);
                if (exists) overwritten++; else imported++;
            }
        } finally {
            deleteTree(staging);
        }
        return new ImportResult(imported, overwritten, skipped, preview.projectName());
    }

    public static int exportWorkspace(Path destination) throws IOException {
        Path root = MCodeProjectManager.getWorkspaceRoot().toAbsolutePath().normalize();
        Files.createDirectories(root);
        List<Path> files;
        try (var walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equalsIgnoreCase("editor-session.dat"))
                    .filter(path -> !path.getFileName().toString().equalsIgnoreCase(MANIFEST))
                    .filter(path -> isProjectFile(root.relativize(path).toString().replace('\\', '/')))
                    .sorted(Comparator.comparing(path -> root.relativize(path).toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
        if (files.isEmpty()) throw new IOException("No hay scripts ni recursos compatibles que exportar.");
        if (files.size() > MAX_FILES) throw new IOException("El workspace supera 10.000 archivos compatibles.");
        long total = 0;
        for (Path file : files) {
            long size = Files.size(file);
            if (size > MAX_SINGLE_FILE) throw new IOException("Archivo demasiado grande: " + file.getFileName());
            total += size;
            if (total > MAX_TOTAL_UNPACKED) throw new IOException("El workspace supera 256 MiB.");
        }
        String baseName = destination.getFileName() == null ? "Proyecto MCODE" : destination.getFileName().toString().replaceFirst("(?i)\\.mcp$", "");
        Path destinationParent = destination.toAbsolutePath().normalize().getParent();
        if (destinationParent == null) throw new IOException("La ruta de destino no es válida.");
        Files.createDirectories(destinationParent);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(destination))) {
            ZipEntry manifest = new ZipEntry(MANIFEST);
            zip.putNextEntry(manifest);
            String json = "{\n  \"format\": \"mcode-project\",\n  \"formatVersion\": 1,\n  \"name\": \"" + jsonEscape(baseName) + "\",\n  \"mcodeVersion\": \"2.4.1\"\n}\n";
            zip.write(json.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (Path file : files) {
                String rel = root.relativize(file).toString().replace('\\', '/');
                ZipEntry entry = new ZipEntry(rel);
                zip.putNextEntry(entry);
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
        return files.size();
    }

    public static boolean isProjectFile(String rawName) {
        String name = rawName == null ? "" : rawName.replace('\\', '/');
        if (name.isBlank() || name.startsWith("/") || name.equals(MANIFEST) || name.equalsIgnoreCase("editor-session.dat")) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = lower.substring(dot);
        return !NEVER_IMPORT.contains(ext) && ALLOWED_EXTENSIONS.contains(ext);
    }

    private static String safeRelativeName(String raw) throws IOException {
        if (raw == null || raw.isBlank() || raw.startsWith("/") || raw.startsWith("\\") || raw.matches("^[A-Za-z]:.*") || raw.indexOf('\\') >= 0) {
            throw new IOException("Ruta absoluta, no portable o vacía dentro del archivo: " + raw);
        }
        String[] segments = raw.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) throw new IOException("Ruta relativa no canónica dentro del archivo: " + raw);
        }
        Path path = Path.of(raw).normalize();
        if (path.isAbsolute() || path.startsWith("..") || !path.toString().replace('\\', '/').equals(raw)) {
            throw new IOException("Ruta que escapa o cambia al normalizar: " + raw);
        }
        return raw;
    }

    private static byte[] readBounded(InputStream in, long max, String error) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        long total = 0;
        int count;
        while ((count = in.read(buffer)) != -1) {
            total += count;
            if (total > max) throw new IOException(error);
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    private static long readBoundedCount(InputStream in, long max, String error) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int count;
        while ((count = in.read(buffer)) != -1) {
            total += count;
            if (total > max) throw new IOException(error);
        }
        return total;
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static String readManifestName(String json) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(json);
        if (!m.find()) return null;
        return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }
}
