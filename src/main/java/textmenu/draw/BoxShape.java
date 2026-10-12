package textmenu.draw;

public final class BoxShape extends Shape {

    private volatile double[] bounds;

    public BoxShape(long id, double x1, double y1, double z1, double x2, double y2, double z2) {
        super(id);
        this.bounds = new double[]{
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)
        };
    }

    public double[] bounds() {
        return bounds;
    }

    @Override
    public double[] center() {
        double[] b = bounds;
        return new double[]{(b[0] + b[3]) / 2.0, (b[1] + b[4]) / 2.0, (b[2] + b[5]) / 2.0};
    }

    @Override
    public int vertexCount() {
        return 8;
    }

    @Override
    public void translate(double dx, double dy, double dz) {
        bounds = shifted(bounds, dx, dy, dz);
        translatePivot(dx, dy, dz);
    }
}
