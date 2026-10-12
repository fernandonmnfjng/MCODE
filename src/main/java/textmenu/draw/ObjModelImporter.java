package textmenu.draw;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Small, dependency-free Wavefront OBJ reader for static Blender exports. */
public final class ObjModelImporter {
    public static final long MAX_FILE_BYTES = 32L * 1024L * 1024L;
    public static final int MAX_POSITIONS = 50_000;
    public static final int MAX_FACES = 50_000;

    private ObjModelImporter() {}

    public record Model(double[] vertices, int[] triangles, int[] edges) {
        public Model {
            vertices = vertices.clone();
            triangles = triangles.clone();
            edges = edges.clone();
        }
        @Override public double[] vertices() { return vertices.clone(); }
        @Override public int[] triangles() { return triangles.clone(); }
        @Override public int[] edges() { return edges.clone(); }
    }

    public static Model read(Path path, String axis) throws IOException {
        long size = Files.size(path);
        if (size <= 0 || size > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("El OBJ debe tener entre 1 byte y 32 MiB.");
        }
        String source = Files.readString(path, StandardCharsets.UTF_8);
        List<double[]> positions = new ArrayList<>();
        List<Integer> triangles = new ArrayList<>();
        Set<Long> boundaryEdges = new LinkedHashSet<>();
        int faceCount = 0;

        String[] lines = source.split("\\R");
        for (int lineNo = 0; lineNo < lines.length; lineNo++) {
            String line = lines[lineNo].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+");
            try {
                switch (parts[0]) {
                    case "v" -> {
                        if (parts.length < 4) throw new IllegalArgumentException("v necesita x y z");
                        if (positions.size() >= MAX_POSITIONS) throw new IllegalArgumentException("El OBJ supera 50.000 vértices.");
                        double x = finite(parts[1]), y = finite(parts[2]), z = finite(parts[3]);
                        if ("blender".equalsIgnoreCase(axis)) {
                            // Blender uses Z-up; map Z-up to Minecraft's Y-up.
                            positions.add(new double[]{x, z, -y});
                        } else if ("xyz".equalsIgnoreCase(axis)) {
                            positions.add(new double[]{x, y, z});
                        } else {
                            throw new IllegalArgumentException("axis debe ser 'blender' o 'xyz'");
                        }
                    }
                    case "f" -> {
                        if (parts.length < 4) throw new IllegalArgumentException("f necesita al menos tres índices");
                        if (++faceCount > MAX_FACES) throw new IllegalArgumentException("El OBJ supera 50.000 caras.");
                        int[] face = new int[parts.length - 1];
                        for (int i = 1; i < parts.length; i++) {
                            String ref = parts[i].split("/", -1)[0];
                            if (ref.isBlank()) throw new IllegalArgumentException("Referencia de vértice vacía");
                            int index = Integer.parseInt(ref);
                            int resolved = index > 0 ? index - 1 : positions.size() + index;
                            if (resolved < 0 || resolved >= positions.size()) throw new IllegalArgumentException("Índice de vértice fuera de rango");
                            face[i - 1] = resolved;
                        }
                        // Triangulate n-gons as a fan; Blender's OBJ exporter commonly emits triangles/quads.
                        for (int i = 1; i < face.length - 1; i++) {
                            triangles.add(face[0]); triangles.add(face[i]); triangles.add(face[i + 1]);
                        }
                        for (int i = 0; i < face.length; i++) {
                            int a = Math.min(face[i], face[(i + 1) % face.length]);
                            int b = Math.max(face[i], face[(i + 1) % face.length]);
                            boundaryEdges.add(((long) a << 32) | (b & 0xffffffffL));
                        }
                    }
                    default -> { /* vt, vn, o, g, s, usemtl and mtllib are ignored in this first static-mesh importer. */ }
                }
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("OBJ línea " + (lineNo + 1) + ": " + ex.getMessage(), ex);
            }
        }
        if (positions.size() < 3 || triangles.isEmpty()) {
            throw new IllegalArgumentException("El OBJ no contiene una malla con vértices y caras válidas.");
        }

        double[] vertices = new double[positions.size() * 3];
        for (int i = 0; i < positions.size(); i++) {
            double[] v = positions.get(i);
            vertices[i * 3] = v[0]; vertices[i * 3 + 1] = v[1]; vertices[i * 3 + 2] = v[2];
        }
        int[] tri = triangles.stream().mapToInt(Integer::intValue).toArray();
        int[] edges = new int[boundaryEdges.size() * 2];
        int out = 0;
        for (long edge : boundaryEdges) { edges[out++] = (int) (edge >>> 32); edges[out++] = (int) edge; }
        return new Model(vertices, tri, edges);
    }

    private static double finite(String value) {
        double result = Double.parseDouble(value);
        if (!Double.isFinite(result) || Math.abs(result) > 1.0e7) throw new IllegalArgumentException("Coordenada no válida o demasiado grande");
        return result;
    }
}
