package textmenu.draw;

public final class LineShape extends Shape {

    private volatile double[] points;

    public LineShape(long id, double[] points) {
        super(id);
        this.points = points.clone();
    }

    public double[] points() {
        return points;
    }

    @Override
    public double[] center() {
        double[] p = points;
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
        return points.length / 3;
    }

    @Override
    public void translate(double dx, double dy, double dz) {
        points = shifted(points, dx, dy, dz);
        translatePivot(dx, dy, dz);
    }
}
