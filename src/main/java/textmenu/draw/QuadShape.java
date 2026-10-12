package textmenu.draw;

public final class QuadShape extends Shape {

    private volatile double[] corners;

    public QuadShape(long id, double[] corners) {
        super(id);
        if (corners.length != 12) throw new IllegalArgumentException("un quad necesita 4 esquinas");
        this.corners = corners.clone();
    }

    public double[] corners() {
        return corners;
    }

    @Override
    public double[] center() {
        double[] p = corners;
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
        return 4;
    }

    @Override
    public void translate(double dx, double dy, double dz) {
        corners = shifted(corners, dx, dy, dz);
        translatePivot(dx, dy, dz);
    }
}
