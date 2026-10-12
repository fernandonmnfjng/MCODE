package textmenu.draw;

import textmenu.runtime.PlayerSnapshot;

public abstract sealed class Shape permits LineShape, BoxShape, QuadShape, MeshShape, TexturedQuadShape, PolygonShape {

    private final long id;
    private volatile Space space = Space.GLOBAL;
    private volatile PlayerSnapshot.Pose anchorPose;
    private volatile boolean followPlayer;
    private volatile int rgb = 0xFFFFFF;
    private volatile float alpha = 1.0f;
    private volatile float thickness = 2.5f;
    private volatile boolean filled;
    private volatile boolean visible = true;
    private volatile String group = "";
    private volatile String owner = "";
    private volatile double pivotX, pivotY, pivotZ;
    private volatile boolean pivotSet;
    private volatile double scaleX = 1.0, scaleY = 1.0, scaleZ = 1.0;
    private volatile double rotateX, rotateY, rotateZ;
    private volatile double cosX = 1.0, sinX, cosY = 1.0, sinY, cosZ = 1.0, sinZ;

    protected Shape(long id) {
        this.id = id;
    }

    public final long id() {
        return id;
    }

    public final Space space() {
        return space;
    }

    public final void space(Space value) {
        this.space = value;
    }

    public final PlayerSnapshot.Pose anchorPose() {
        return anchorPose;
    }

    public final boolean followPlayer() {
        return followPlayer;
    }

    public final void localTransform(PlayerSnapshot.Pose anchor, boolean follow) {
        this.space = Space.LOCAL;
        this.anchorPose = anchor;
        this.followPlayer = follow;
    }

    public final int rgb() {
        return rgb;
    }

    public final void rgb(int value) {
        this.rgb = value & 0xFFFFFF;
    }

    public final float alpha() {
        return alpha;
    }

    public final void alpha(float value) {
        this.alpha = value;
    }

    public final float thickness() {
        return thickness;
    }

    public final void thickness(float value) {
        this.thickness = value;
    }

    public final boolean filled() {
        return filled;
    }

    public final void filled(boolean value) {
        this.filled = value;
    }

    public final boolean visible() {
        return visible;
    }

    public final void visible(boolean value) {
        this.visible = value;
    }

    public final String owner() { return owner; }
    public final void owner(String value) { owner = value == null ? "" : value; }

    public final void transformPivot(double[] center) {
        if (center == null || center.length < 3) return;
        pivotX = center[0]; pivotY = center[1]; pivotZ = center[2]; pivotSet = true;
    }

    protected final void translatePivot(double dx, double dy, double dz) {
        if (pivotSet) { pivotX += dx; pivotY += dy; pivotZ += dz; }
    }

    public final void rotateBy(double xDegrees, double yDegrees, double zDegrees) {
        rotateX += xDegrees; rotateY += yDegrees; rotateZ += zDegrees;
        updateRotationCache();
    }

    private void updateRotationCache() {
        double rx = Math.toRadians(rotateX), ry = Math.toRadians(rotateY), rz = Math.toRadians(rotateZ);
        cosX = Math.cos(rx); sinX = Math.sin(rx);
        cosY = Math.cos(ry); sinY = Math.sin(ry);
        cosZ = Math.cos(rz); sinZ = Math.sin(rz);
    }

    public final void scaleBy(double x, double y, double z) {
        scaleX *= x; scaleY *= y; scaleZ *= z;
    }

    public final void resetTransform() {
        scaleX = scaleY = scaleZ = 1.0;
        rotateX = rotateY = rotateZ = 0.0;
        updateRotationCache();
    }

    public final void pivotPosition(double[] out) {
        if (pivotSet) { out[0] = pivotX; out[1] = pivotY; out[2] = pivotZ; }
        else { double[] c = center(); out[0] = c[0]; out[1] = c[1]; out[2] = c[2]; }
    }

    public final void transformPoint(double x, double y, double z, double[] out) {
        if (!pivotSet) { out[0] = x; out[1] = y; out[2] = z; return; }
        double px = (x - pivotX) * scaleX, py = (y - pivotY) * scaleY, pz = (z - pivotZ) * scaleZ;
        double ny = py * cosX - pz * sinX, nz = py * sinX + pz * cosX;
        py = ny; pz = nz;
        double nx = px * cosY + pz * sinY; nz = -px * sinY + pz * cosY;
        px = nx; pz = nz;
        nx = px * cosZ - py * sinZ; ny = px * sinZ + py * cosZ;
        out[0] = nx + pivotX; out[1] = ny + pivotY; out[2] = pz + pivotZ;
    }

    public abstract double[] center();

    public final String group() {
        return group;
    }

    public final void group(String value) {
        this.group = value == null ? "" : value;
    }

    public abstract int vertexCount();

    public abstract void translate(double dx, double dy, double dz);

    protected void release() {
    }

    protected static double[] shifted(double[] source, double dx, double dy, double dz) {
        double[] out = new double[source.length];
        for (int i = 0; i < source.length; i += 3) {
            out[i] = source[i] + dx;
            out[i + 1] = source[i + 1] + dy;
            out[i + 2] = source[i + 2] + dz;
        }
        return out;
    }
}
