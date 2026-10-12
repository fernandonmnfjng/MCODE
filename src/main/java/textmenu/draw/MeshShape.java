package textmenu.draw;

public final class MeshShape extends Shape {

    private volatile double[] vertices;
    private final int[] triangles;
    private final int[] edges;

    public MeshShape(long id, double[] vertices, int[] triangles) {
        this(id, vertices, triangles, triangleEdges(triangles));
    }

    public MeshShape(long id, double[] vertices, int[] triangles, int[] edges) {
        super(id);
        if (vertices == null || vertices.length < 9 || vertices.length % 3 != 0) throw new IllegalArgumentException("mesh necesita al menos tres vertices XYZ");
        if (triangles == null || triangles.length % 3 != 0) throw new IllegalArgumentException("las caras deben ser triangulos");
        if (edges == null || edges.length % 2 != 0) throw new IllegalArgumentException("los bordes deben ser pares de indices");
        int count = vertices.length / 3;
        for (int index : triangles) if (index < 0 || index >= count) throw new IllegalArgumentException("indice de triangulo fuera de rango: " + index);
        for (int index : edges) if (index < 0 || index >= count) throw new IllegalArgumentException("indice de borde fuera de rango: " + index);
        this.vertices = vertices.clone();
        this.triangles = triangles.clone();
        this.edges = edges.clone();
    }

    private static int[] triangleEdges(int[] triangles) {
        java.util.LinkedHashSet<Long> unique = new java.util.LinkedHashSet<>();
        for (int i = 0; i + 2 < triangles.length; i += 3) {
            addEdge(unique, triangles[i], triangles[i + 1]);
            addEdge(unique, triangles[i + 1], triangles[i + 2]);
            addEdge(unique, triangles[i + 2], triangles[i]);
        }
        int[] out = new int[unique.size() * 2]; int j = 0;
        for (long edge : unique) { out[j++] = (int)(edge >>> 32); out[j++] = (int)edge; }
        return out;
    }

    private static void addEdge(java.util.Set<Long> edges, int a, int b) {
        int low = Math.min(a, b), high = Math.max(a, b);
        edges.add(((long) low << 32) | (high & 0xffffffffL));
    }

    public double[] vertices() {
        return vertices;
    }

    public int[] triangles() {
        return triangles;
    }

    public int[] edges() { return edges; }

    @Override
    public double[] center() {
        double[] p = vertices;
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i + 2 < p.length; i += 3) {
            minX = Math.min(minX, p[i]); maxX = Math.max(maxX, p[i]);
            minY = Math.min(minY, p[i + 1]); maxY = Math.max(maxY, p[i + 1]);
            minZ = Math.min(minZ, p[i + 2]); maxZ = Math.max(maxZ, p[i + 2]);
        }
        return new double[]{(minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0};
    }

    @Override
    public int vertexCount() {
        return vertices.length / 3;
    }

    @Override
    public void translate(double dx, double dy, double dz) {
        vertices = shifted(vertices, dx, dy, dz);
        translatePivot(dx, dy, dz);
    }
}
