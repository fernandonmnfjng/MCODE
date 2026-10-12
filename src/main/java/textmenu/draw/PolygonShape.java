package textmenu.draw;

/** A planar contour with optional triangulation for its filled area. */
public final class PolygonShape extends Shape {

    private volatile double[] vertices;
    private final int[] triangles;
    private final int[] boundary;

    public PolygonShape(long id, double[] vertices, int[] triangles, int[] boundary) {
        super(id);
        if (vertices == null || vertices.length % 3 != 0 || vertices.length < 9) {
            throw new IllegalArgumentException("un polígono necesita al menos tres vértices 3D");
        }
        if (boundary == null || boundary.length < 3) {
            throw new IllegalArgumentException("el contorno necesita al menos tres vértices");
        }
        if (triangles == null || triangles.length % 3 != 0) {
            throw new IllegalArgumentException("las caras del polígono deben ser triángulos");
        }
        int count = vertices.length / 3;
        for (int index : boundary) {
            if (index < 0 || index >= count) throw new IllegalArgumentException("índice de contorno fuera de rango");
        }
        for (int index : triangles) {
            if (index < 0 || index >= count) throw new IllegalArgumentException("índice de triángulo fuera de rango");
        }
        this.vertices = vertices.clone();
        this.triangles = triangles.clone();
        this.boundary = boundary.clone();
    }

    public double[] vertices() {
        return vertices;
    }

    public int[] triangles() {
        return triangles;
    }

    public int[] boundary() {
        return boundary;
    }

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
